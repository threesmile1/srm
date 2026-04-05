import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const KEY_USER = 'srm_portal_username'
const KEY_SID = 'srm_portal_supplier_id'

/** 门户登录：一期无后端认证，会话内绑定供应商数据范围（与 API supplierId 一致） */
export const usePortalAuthStore = defineStore('portalAuth', () => {
  const username = ref(sessionStorage.getItem(KEY_USER) || '')
  const supplierId = ref(sessionStorage.getItem(KEY_SID) || import.meta.env.VITE_DEV_SUPPLIER_ID || '1')

  const isLoggedIn = computed(() => Boolean(username.value))

  function login(user: string, password: string, sid: string) {
    const u = user.trim()
    if (!u) throw new Error('请输入账号')
    if (!password) throw new Error('请输入密码')
    const id = sid.trim() || '1'
    if (!/^\d+$/.test(id)) throw new Error('供应商编号须为数字')
    if (u === 'portal' && password !== 'portal123') {
      throw new Error('账号或密码错误')
    }
    username.value = u
    supplierId.value = id
    sessionStorage.setItem(KEY_USER, u)
    sessionStorage.setItem(KEY_SID, id)
  }

  function logout() {
    username.value = ''
    sessionStorage.removeItem(KEY_USER)
    sessionStorage.removeItem(KEY_SID)
    supplierId.value = import.meta.env.VITE_DEV_SUPPLIER_ID || '1'
  }

  return { username, supplierId, isLoggedIn, login, logout }
})
