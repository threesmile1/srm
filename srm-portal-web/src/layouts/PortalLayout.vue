<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { Document, Van, SwitchButton, User } from '@element-plus/icons-vue'
import { usePortalAuthStore } from '../stores/portalAuth'

const route = useRoute()
const router = useRouter()
const auth = usePortalAuthStore()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="zy-shell">
    <header class="zy-topbar">
      <div class="zy-topbar-inner">
        <div class="zy-topbar-left">
          <div class="zy-product" @click="router.push('/pos')">
            <img class="zy-product-logo" src="/paterson-logo.png" alt="百得胜家居" />
            <div class="zy-product-text">
              <span class="zy-product-name">百得胜采购协同</span>
              <span class="zy-product-sub">供应商门户</span>
            </div>
          </div>
          <nav class="zy-tabs" aria-label="主导航">
            <router-link to="/pos" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/pos') }">
              <el-icon><Document /></el-icon>
              采购订单
            </router-link>
            <router-link to="/asn" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/asn') }">
              <el-icon><Van /></el-icon>
              发货通知 ASN
            </router-link>
          </nav>
        </div>
        <div class="zy-topbar-right">
          <div class="zy-user-block">
            <span class="zy-user-avatar">
              <el-icon><User /></el-icon>
            </span>
            <div class="zy-user-meta">
              <span class="zy-user-name">{{ auth.username }}</span>
              <span class="zy-user-tag">供应商编号 {{ auth.supplierId }}</span>
            </div>
          </div>
          <el-divider direction="vertical" class="zy-divider" />
          <el-button type="primary" link class="zy-logout" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </div>
    </header>

    <main class="zy-body">
      <div class="zy-body-card">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.zy-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.zy-topbar {
  background: #fff;
  border-bottom: 1px solid var(--zy-header-border, #e8e8e8);
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.04);
  flex-shrink: 0;
}

.zy-topbar-inner {
  height: 52px;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.zy-topbar-left {
  display: flex;
  align-items: center;
  gap: 32px;
  min-width: 0;
}

.zy-product {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex-shrink: 0;
}

.zy-product-logo {
  height: 34px;
  width: auto;
  max-width: 110px;
  object-fit: contain;
}

.zy-product-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.zy-product-name {
  font-size: 15px;
  font-weight: 600;
  color: #262626;
}

.zy-product-sub {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.zy-tabs {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 52px;
}

.zy-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 52px;
  padding: 0 16px;
  font-size: 14px;
  color: #595959;
  text-decoration: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition:
    color 0.15s,
    border-color 0.15s;
}

.zy-tab:hover {
  color: var(--zy-tab-active, #1677ff);
}

.zy-tab.is-active {
  color: var(--zy-tab-active, #1677ff);
  font-weight: 500;
  border-bottom-color: var(--zy-tab-active, #1677ff);
}

.zy-topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.zy-user-block {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px;
  border-radius: 4px;
}

.zy-user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1677ff, #0958d9);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.zy-user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.zy-user-name {
  font-size: 13px;
  color: #262626;
  font-weight: 500;
}

.zy-user-tag {
  font-size: 12px;
  color: #8c8c8c;
}

.zy-divider {
  margin: 0 8px;
  height: 24px;
}

.zy-logout {
  font-size: 13px;
  color: #595959 !important;
}

.zy-logout:hover {
  color: var(--el-color-primary) !important;
}

.zy-body {
  flex: 1;
  padding: 16px;
  overflow: auto;
}

.zy-body-card {
  max-width: 1440px;
  margin: 0 auto;
  min-height: calc(100vh - 52px - 32px);
  background: #fff;
  border-radius: 4px;
  border: 1px solid var(--zy-card-border, #f0f0f0);
  padding: 20px 24px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}
</style>
