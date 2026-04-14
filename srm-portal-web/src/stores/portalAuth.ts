import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api/http'

type UserInfo = {
  id: number
  username: string
  displayName: string
  roles: string[]
  defaultProcurementOrgId: number | null
  supplierId: number | null
  supplierName?: string | null
}

const STORAGE_KEY = 'srm_portal_user'

export const usePortalAuthStore = defineStore('portalAuth', () => {
  const stored = sessionStorage.getItem(STORAGE_KEY)
  const user = ref<UserInfo | null>(stored ? JSON.parse(stored) : null)

  const isLoggedIn = computed(() => user.value !== null)
  const username = computed(() => user.value?.username ?? '')
  /** 会话中的供应商主键；登录已校验非空，未登录时为 null（不再默认 1，避免前端误判） */
  const supplierId = computed(() => user.value?.supplierId ?? null)
  const supplierName = computed(() => user.value?.supplierName ?? null)

  async function login(name: string, password: string) {
    const u = name.trim()
    if (!u) throw new Error('请输入账号')
    if (!password) throw new Error('请输入密码')
    try {
      const res = await api.post<UserInfo>('/api/v1/auth/login', { username: u, password })
      if (!res.data.supplierId) {
        throw new Error('该账号不是供应商用户')
      }
      user.value = res.data
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(res.data))
    } catch (e: unknown) {
      const msg =
        e instanceof Error
          ? e.message
          : e && typeof e === 'object' && 'response' in e
            ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
            : ''
      throw new Error(msg || '账号或密码错误')
    }
  }

  async function logout() {
    try {
      await api.post('/api/v1/auth/logout')
    } catch {
      /* ignore */
    }
    user.value = null
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return { user, username, supplierId, supplierName, isLoggedIn, login, logout }
})
