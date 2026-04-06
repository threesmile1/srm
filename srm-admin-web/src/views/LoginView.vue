<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('admin')
const password = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    ElMessage.success('登录成功')
    await router.replace((router.currentRoute.value.query.redirect as string) || '/home')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-root">
    <div class="login-bg" aria-hidden="true" />
    <div class="login-card">
      <div class="login-brand">
        <img class="logo-img" src="/paterson-logo.png" alt="百得胜家居" />
        <h1>百得胜采购协同平台</h1>
        <p class="sub">管理端 · 采购执行与主数据</p>
      </div>
      <el-form class="login-form" @submit.prevent="submit">
        <el-form-item>
          <el-input
            v-model="username"
            size="large"
            placeholder="用户名"
            :prefix-icon="User"
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            size="large"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" native-type="submit">
          登 录
        </el-button>
        <p class="hint">演示账号：<strong>admin</strong> / <strong>admin123</strong></p>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 24px;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(135deg, #4a0d18 0%, #8b1538 38%, #1a2744 100%),
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(255, 255, 255, 0.08), transparent),
    radial-gradient(ellipse 60% 50% at 90% 80%, rgba(80, 10, 30, 0.4), transparent);
  z-index: 0;
}

.login-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  opacity: 0.6;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 12px;
  box-shadow:
    0 4px 24px rgba(0, 36, 77, 0.12),
    0 0 1px rgba(0, 36, 77, 0.08);
  padding: 40px 40px 32px;
}

.login-brand {
  text-align: center;
  margin-bottom: 32px;
}

.logo-img {
  display: block;
  width: auto;
  max-width: 220px;
  height: 56px;
  margin: 0 auto 16px;
  object-fit: contain;
}

.login-brand h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.5px;
}

.sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.login-form {
  margin-top: 8px;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  font-weight: 600;
  letter-spacing: 4px;
  text-indent: 4px;
}

.hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}

</style>
