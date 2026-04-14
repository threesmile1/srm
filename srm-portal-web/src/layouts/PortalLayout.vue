<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, Document, Van, SwitchButton, User, Money, WarningFilled, Sell, Tickets, Medal, Warning,
} from '@element-plus/icons-vue'
import { usePortalAuthStore } from '../stores/portalAuth'
import { portalApi, type PortalTodoSummary } from '../api/portal'
import { portalNotificationApi } from '../api/notification'

const route = useRoute()
const router = useRouter()
const auth = usePortalAuthStore()

const todo = ref<PortalTodoSummary>({ pendingConfirmLines: 0, asnNoticeCount: 0, pendingRfqQuotations: 0 })
const todoLoaded = ref(false)

async function loadTodoSummary() {
  if (!auth.isLoggedIn) return
  try {
    const r = await portalApi.todoSummary()
    todo.value = r.data
  } catch {
    todo.value = { pendingConfirmLines: 0, asnNoticeCount: 0, pendingRfqQuotations: 0 }
  } finally {
    todoLoaded.value = true
  }
}

watch(
  () => route.fullPath,
  () => loadTodoSummary(),
  { immediate: true },
)

const unreadNotifications = ref(0)
let notifPollTimer: ReturnType<typeof setInterval> | undefined

async function pollUnreadNotifications() {
  if (!auth.isLoggedIn) return
  try {
    const r = await portalNotificationApi.unreadCount()
    unreadNotifications.value = r.data.count ?? 0
  } catch {
    unreadNotifications.value = 0
  }
}

onMounted(() => {
  pollUnreadNotifications()
  notifPollTimer = setInterval(pollUnreadNotifications, 60_000)
})

onUnmounted(() => {
  if (notifPollTimer) clearInterval(notifPollTimer)
})

watch(
  () => route.path,
  (p) => {
    if (p === '/notifications') pollUnreadNotifications()
  },
)

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
            <router-link to="/rfq" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/rfq') }">
              <el-icon><Sell /></el-icon>
              询价报价
            </router-link>
            <router-link to="/invoices" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/invoices') }">
              <el-icon><Money /></el-icon>
              发票
            </router-link>
            <router-link
              to="/reconciliation"
              class="zy-tab"
              :class="{ 'is-active': route.path.startsWith('/reconciliation') }"
            >
              <el-icon><Document /></el-icon>
              对账
            </router-link>
            <router-link to="/contracts" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/contracts') }">
              <el-icon><Tickets /></el-icon>
              合同
            </router-link>
            <router-link to="/perf" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/perf') }">
              <el-icon><Medal /></el-icon>
              绩效
            </router-link>
            <router-link to="/quality" class="zy-tab" :class="{ 'is-active': route.path.startsWith('/quality') }">
              <el-icon><Warning /></el-icon>
              质量
            </router-link>
            <router-link
              to="/notifications"
              class="zy-tab"
              :class="{ 'is-active': route.path.startsWith('/notifications') }"
            >
              <el-icon><Bell /></el-icon>
              消息
            </router-link>
          </nav>
        </div>
        <div class="zy-topbar-right">
          <router-link to="/notifications" class="zy-bell-wrap" title="消息中心">
            <el-badge :value="unreadNotifications" :hidden="unreadNotifications === 0" :max="99" class="zy-bell-badge">
              <el-icon class="zy-bell-icon" :size="22"><Bell /></el-icon>
            </el-badge>
          </router-link>
          <el-divider direction="vertical" class="zy-divider" />
          <div class="zy-user-block">
            <span class="zy-user-avatar">
              <el-icon><User /></el-icon>
            </span>
            <div class="zy-user-meta">
              <span class="zy-user-name">{{ auth.username }}</span>
              <span class="zy-user-tag">供应商 {{ auth.supplierName || '—' }}</span>
            </div>
          </div>
          <el-button type="primary" link class="zy-logout" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </div>
    </header>

    <div v-if="todoLoaded" class="zy-todo-band" aria-label="待办摘要">
      <div class="zy-todo-inner">
        <router-link
          to="/pos"
          class="zy-todo-chip"
          :class="{ 'zy-todo-chip--warn': todo.pendingConfirmLines > 0 }"
        >
          <el-icon v-if="todo.pendingConfirmLines > 0" class="zy-todo-warn-ic"><WarningFilled /></el-icon>
          <span class="zy-todo-num">{{ todo.pendingConfirmLines }}</span>
          <span class="zy-todo-lbl">待确认订单行</span>
          <span class="zy-todo-go">去处理</span>
        </router-link>
        <router-link to="/asn" class="zy-todo-chip">
          <span class="zy-todo-num">{{ todo.asnNoticeCount }}</span>
          <span class="zy-todo-lbl">发货通知 ASN</span>
          <span class="zy-todo-go">查看</span>
        </router-link>
        <router-link
          to="/rfq"
          class="zy-todo-chip"
          :class="{ 'zy-todo-chip--warn': (todo.pendingRfqQuotations ?? 0) > 0 }"
        >
          <el-icon v-if="(todo.pendingRfqQuotations ?? 0) > 0" class="zy-todo-warn-ic"><WarningFilled /></el-icon>
          <span class="zy-todo-num">{{ todo.pendingRfqQuotations ?? 0 }}</span>
          <span class="zy-todo-lbl">待报价询价</span>
          <span class="zy-todo-go">去报价</span>
        </router-link>
        <router-link
          to="/notifications"
          class="zy-todo-chip"
          :class="{ 'zy-todo-chip--warn': unreadNotifications > 0 }"
        >
          <el-icon v-if="unreadNotifications > 0" class="zy-todo-warn-ic"><WarningFilled /></el-icon>
          <span class="zy-todo-num">{{ unreadNotifications }}</span>
          <span class="zy-todo-lbl">未读消息</span>
          <span class="zy-todo-go">查看</span>
        </router-link>
      </div>
    </div>

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

.zy-todo-band {
  background: linear-gradient(180deg, #fafbff 0%, #f5f7fa 100%);
  border-bottom: 1px solid var(--zy-header-border, #e8e8e8);
  flex-shrink: 0;
}
.zy-todo-inner {
  max-width: 1440px;
  margin: 0 auto;
  padding: 10px 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.zy-todo-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  text-decoration: none;
  color: #374151;
  font-size: 13px;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.zy-todo-chip:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}
.zy-todo-chip--warn {
  border-color: #f5dab1;
  background: #fffbeb;
}
.zy-todo-warn-ic {
  color: #e6a23c;
  font-size: 16px;
}
.zy-todo-num {
  font-weight: 700;
  font-size: 16px;
  color: #111827;
  min-width: 1.25em;
}
.zy-todo-lbl {
  color: #4b5563;
}
.zy-todo-go {
  margin-left: 4px;
  color: #409eff;
  font-weight: 500;
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

.zy-bell-wrap {
  display: inline-flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
  color: #595959;
  text-decoration: none;
}

.zy-bell-wrap:hover {
  background: #f5f5f5;
  color: #1677ff;
}

.zy-bell-badge :deep(.el-badge__content) {
  transform: translate(6px, -4px);
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
