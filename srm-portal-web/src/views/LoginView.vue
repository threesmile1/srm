<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { usePortalAuthStore } from '../stores/portalAuth'

const router = useRouter()
const auth = usePortalAuthStore()

const username = ref('portal')
const password = ref('')
const supplierId = ref('1')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    auth.login(username.value, password.value, supplierId.value)
    ElMessage.success('登录成功')
    await router.replace((router.currentRoute.value.query.redirect as string) || '/pos')
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
        <p class="sub">供应商门户 · 订单确认 · 发货通知 ASN</p>
      </div>
      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item>
          <el-input
            v-model="username"
            size="large"
            placeholder="登录账号"
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
        <el-form-item label="供应商编号" class="sid-item">
          <el-input
            v-model="supplierId"
            size="large"
            placeholder="数据范围（对应后端 supplierId）"
            :prefix-icon="Key"
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" native-type="submit">
          登 录
        </el-button>
        <p class="hint">演示：<strong>portal</strong> / <strong>portal123</strong> · 供应商编号种子一般为 <strong>1</strong></p>
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
    linear-gradient(145deg, #4a0d18 0%, #8b1538 40%, #1a2744 100%),
    radial-gradient(ellipse 70% 50% at 15% 20%, rgba(255, 255, 255, 0.08), transparent);
  z-index: 0;
}

.login-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 40px 40px;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 440px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(12, 74, 110, 0.15);
  padding: 36px 40px 28px;
}

.login-brand {
  text-align: center;
  margin-bottom: 28px;
}

.logo-img {
  display: block;
  width: auto;
  max-width: 220px;
  height: 52px;
  margin: 0 auto 14px;
  object-fit: contain;
}

.login-brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}

.sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.login-form {
  margin-top: 4px;
}

.sid-item :deep(.el-form-item__label) {
  font-size: 13px;
  color: #4b5563;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
  font-weight: 600;
  letter-spacing: 4px;
  text-indent: 4px;
}

.hint {
  margin: 14px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  line-height: 1.5;
}

</style>
