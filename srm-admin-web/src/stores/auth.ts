import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type UserInfo } from '../api/auth'

const STORAGE_KEY = 'srm_admin_user'

export const useAuthStore = defineStore('auth', () => {
  const stored = sessionStorage.getItem(STORAGE_KEY)
  const user = ref<UserInfo | null>(stored ? JSON.parse(stored) : null)

  const isLoggedIn = computed(() => user.value !== null)
  const userId = computed(() => user.value?.id ?? null)
  const username = computed(() => user.value?.username ?? '')
  const displayName = computed(() => user.value?.displayName ?? '')
  const roles = computed(() => user.value?.roles ?? [])
  const supplierId = computed(() => user.value?.supplierId ?? null)

  function hasRole(role: string): boolean {
    return roles.value.includes(role) || roles.value.includes('ADMIN')
  }

  async function login(name: string, password: string) {
    const u = name.trim()
    if (!u) throw new Error('请输入用户名')
    if (!password) throw new Error('请输入密码')
    try {
      const res = await authApi.login(u, password)
      user.value = res.data
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(res.data))
    } catch (e: unknown) {
      const msg =
        e && typeof e === 'object' && 'response' in e
          ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
          : ''
      throw new Error(msg || '用户名或密码错误')
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* ignore */
    }
    user.value = null
    sessionStorage.removeItem(STORAGE_KEY)
  }

  async function refresh() {
    try {
      const res = await authApi.me()
      user.value = res.data
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(res.data))
    } catch {
      user.value = null
      sessionStorage.removeItem(STORAGE_KEY)
    }
  }

  return { user, userId, username, displayName, isLoggedIn, roles, supplierId, hasRole, login, logout, refresh }
})
