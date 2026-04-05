<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HomeFilled,
  OfficeBuilding,
  Goods,
  Shop,
  Document,
  EditPen,
  Van,
  Plus,
  DataAnalysis,
  SwitchButton,
  Fold,
  Expand,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const collapsed = ref(false)
const asideWidth = computed(() => (collapsed.value ? 64 : 220))

const breadcrumbs = computed(() => {
  const m = route.meta.title as string | undefined
  return m ? [{ title: m }] : []
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="zy-layout">
    <el-aside :width="`${asideWidth}px`" class="zy-aside" :class="{ 'is-collapsed': collapsed }">
      <div class="zy-brand" @click="router.push('/home')">
        <img class="zy-brand-logo" src="/paterson-logo.png" alt="百得胜家居" />
        <div v-show="!collapsed" class="zy-brand-text">
          <span class="zy-brand-name">百得胜家居</span>
          <span class="zy-brand-sub">采购协同平台</span>
        </div>
      </div>
      <el-scrollbar class="zy-menu-scroll">
        <el-menu
          :key="route.path + String(collapsed)"
          :default-active="route.path"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          class="zy-menu"
          background-color="transparent"
          text-color="rgba(255, 255, 255, 0.65)"
          active-text-color="#ffffff"
        >
          <el-menu-item index="/home">
            <el-icon><HomeFilled /></el-icon>
            <template #title>工作台</template>
          </el-menu-item>
          <el-sub-menu index="master" popper-class="zy-menu-popper">
            <template #title>
              <el-icon><OfficeBuilding /></el-icon>
              <span>主数据</span>
            </template>
            <el-menu-item index="/master/suppliers">
              <el-icon><Shop /></el-icon>
              <template #title>供应商</template>
            </el-menu-item>
            <el-menu-item index="/master/materials">
              <el-icon><Goods /></el-icon>
              <template #title>物料</template>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="po" popper-class="zy-menu-popper">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>采购执行</span>
            </template>
            <el-menu-item index="/purchase/orders">
              <el-icon><Document /></el-icon>
              <template #title>订单列表</template>
            </el-menu-item>
            <el-menu-item index="/purchase/orders/new">
              <el-icon><EditPen /></el-icon>
              <template #title>新建订单</template>
            </el-menu-item>
            <el-menu-item index="/purchase/receipts">
              <el-icon><Van /></el-icon>
              <template #title>收货单</template>
            </el-menu-item>
            <el-menu-item index="/purchase/receipts/new">
              <el-icon><Plus /></el-icon>
              <template #title>新建收货</template>
            </el-menu-item>
            <el-menu-item index="/purchase/reports/execution">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>执行报表</template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="zy-right">
      <el-header class="zy-header">
        <div class="zy-header-left">
          <el-button class="zy-fold" text @click="collapsed = !collapsed">
            <el-icon :size="18">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/" class="zy-crumb">
            <el-breadcrumb-item>
              <router-link to="/home" class="zy-crumb-link">首页</router-link>
            </el-breadcrumb-item>
            <el-breadcrumb-item v-for="(b, i) in breadcrumbs" :key="i">{{ b.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="zy-header-right">
          <el-dropdown trigger="click" @command="(c: string) => c === 'logout' && logout()">
            <span class="zy-user-trigger">
              <span class="zy-user-avatar">
                <el-icon><User /></el-icon>
              </span>
              <span class="zy-user-name">{{ auth.displayName || auth.username }}</span>
              <span class="zy-user-caret">▼</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <span class="zy-dd-item">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="zy-main">
        <div class="zy-main-card">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.zy-layout {
  min-height: 100vh;
  background: #f0f2f5;
}

.zy-aside {
  background: var(--zy-sidebar, #0c1421);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s ease;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.zy-brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  cursor: pointer;
}

.zy-brand-logo {
  height: 32px;
  width: auto;
  max-width: 108px;
  object-fit: contain;
  flex-shrink: 0;
  filter: brightness(1.08);
}

.zy-aside.is-collapsed .zy-brand {
  justify-content: center;
  padding: 0 8px;
}

.zy-aside.is-collapsed .zy-brand-logo {
  max-width: 44px;
}

.zy-brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.zy-brand-name {
  font-weight: 600;
  font-size: 14px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.zy-brand-sub {
  font-size: 11px;
  color: var(--zy-text-muted, rgba(255, 255, 255, 0.55));
  margin-top: 2px;
}

.zy-menu-scroll {
  flex: 1;
  min-height: 0;
}

.zy-menu {
  border-right: none !important;
  padding: 8px 0 20px;
}

.zy-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 甄云式：选中项左侧色条 + 浅蓝底 */
.zy-menu :deep(.el-menu-item) {
  margin: 2px 8px;
  border-radius: 4px;
  height: 42px;
}

.zy-menu :deep(.el-sub-menu .el-menu-item) {
  margin: 2px 8px 2px 12px;
  padding-left: 44px !important;
  min-width: auto;
}

.zy-menu :deep(.el-menu-item:hover),
.zy-menu :deep(.el-sub-menu__title:hover) {
  background: var(--zy-sidebar-hover, rgba(255, 255, 255, 0.06)) !important;
}

.zy-menu :deep(.el-menu-item.is-active) {
  background: var(--zy-menu-active-bg, rgba(22, 119, 255, 0.18)) !important;
  color: #fff !important;
  border-left: 3px solid var(--zy-menu-accent, #1677ff);
  padding-left: calc(var(--el-menu-base-level-padding) + 3px) !important;
}

.zy-menu.el-menu--collapse :deep(.el-menu-item.is-active) {
  border-left: none;
  padding-left: 20px !important;
}

.zy-menu :deep(.el-sub-menu__title) {
  margin: 2px 8px;
  border-radius: 4px;
  height: 42px;
}

.zy-menu :deep(.el-sub-menu .el-menu) {
  background: transparent !important;
}

.zy-right {
  flex-direction: column;
  min-width: 0;
  background: #f0f2f5;
}

.zy-header {
  height: 48px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 4px;
  border-bottom: 1px solid var(--zy-header-border, #e8e8e8);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.02);
  z-index: 5;
}

.zy-header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.zy-fold {
  color: #595959;
  padding: 8px 10px;
}

.zy-fold:hover {
  color: var(--zy-menu-accent, #1677ff);
  background: rgba(22, 119, 255, 0.06);
}

.zy-crumb {
  font-size: 13px;
}

.zy-crumb :deep(.el-breadcrumb__inner) {
  color: #8c8c8c;
  font-weight: 400;
}

.zy-crumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #262626;
  font-weight: 500;
}

.zy-crumb-link {
  color: #8c8c8c;
  text-decoration: none;
}

.zy-crumb-link:hover {
  color: var(--zy-menu-accent, #1677ff);
}

.zy-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.zy-user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 4px;
  outline: none;
}

.zy-user-trigger:hover {
  background: #f5f5f5;
}

.zy-user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1677ff, #0958d9);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.zy-user-name {
  font-size: 13px;
  color: #262626;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zy-user-caret {
  font-size: 10px;
  color: #bfbfbf;
  transform: scale(0.85);
}

.zy-dd-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.zy-main {
  padding: 16px;
  overflow: auto;
  background: #f0f2f5;
}

.zy-main-card {
  min-height: calc(100vh - 48px - 32px);
  background: #fff;
  border-radius: 4px;
  border: 1px solid var(--zy-card-border, #f0f0f0);
  padding: 20px 24px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}
</style>
