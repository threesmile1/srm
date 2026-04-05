import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const KEY_USER = 'srm_admin_username'
const KEY_DISPLAY = 'srm_admin_display'

/** 一期：无后端登录接口，本地会话 + 演示账号校验（与 README dev 种子一致） */
export const useAuthStore = defineStore('auth', () => {
  const username = ref(sessionStorage.getItem(KEY_USER) || '')
  const displayName = ref(sessionStorage.getItem(KEY_DISPLAY) || '')

  const isLoggedIn = computed(() => Boolean(username.value))

  function login(user: string, password: string) {
    const u = user.trim()
    if (!u) throw new Error('请输入用户名')
    if (!password) throw new Error('请输入密码')
    // 演示：admin/admin123；其它账号密码非空也可登录便于联调
    if (u === 'admin' && password !== 'admin123') {
      throw new Error('用户名或密码错误')
    }
    username.value = u
    displayName.value = u === 'admin' ? '系统管理员' : u
    sessionStorage.setItem(KEY_USER, username.value)
    sessionStorage.setItem(KEY_DISPLAY, displayName.value)
  }

  function logout() {
    username.value = ''
    displayName.value = ''
    sessionStorage.removeItem(KEY_USER)
    sessionStorage.removeItem(KEY_DISPLAY)
  }

  return { username, displayName, isLoggedIn, login, logout }
})
