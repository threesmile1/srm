<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { dashboardApi, type DashboardStats, type MonthAmount, type GradeCount, type PendingItem } from '../api/dashboard'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  Tickets, Money, Box, Document, Bell,
} from '@element-plus/icons-vue'
import { EMPTY_DASHBOARD_DESC, EMPTY_DASHBOARD_HINT } from '../constants/emptyCopy'

use([BarChart, PieChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const auth = useAuthStore()
const orgId = auth.user?.defaultProcurementOrgId

const stats = ref<DashboardStats | null>(null)
const pending = ref<PendingItem[]>([])
const barOption = shallowRef({})
const pieOption = shallowRef({})
const loading = ref(true)

function buildBarOption(data: MonthAmount[]) {
  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 60, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category' as const, data: data.map(d => d.month) },
    yAxis: { type: 'value' as const },
    series: [{
      type: 'bar' as const,
      data: data.map(d => Number(d.amount)),
      itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 36,
    }],
  }
}

function buildPieOption(data: GradeCount[]) {
  const colorMap: Record<string, string> = { A: '#67C23A', B: '#409EFF', C: '#E6A23C', D: '#F56C6C' }
  return {
    tooltip: { trigger: 'item' as const },
    legend: { bottom: 0 },
    series: [{
      type: 'pie' as const,
      radius: ['40%', '65%'],
      label: { show: true, formatter: '{b}: {c}' },
      data: data.map(d => ({
        name: d.grade + '级',
        value: d.count,
        itemStyle: { color: colorMap[d.grade] || '#909399' },
      })),
    }],
  }
}

async function load() {
  if (!orgId) { loading.value = false; return }
  loading.value = true
  try {
    const [s, trend, perf, items] = await Promise.all([
      dashboardApi.stats(orgId),
      dashboardApi.poTrend(orgId),
      dashboardApi.perfDistribution(),
      dashboardApi.pendingItems(),
    ])
    stats.value = s.data
    pending.value = items.data
    barOption.value = buildBarOption(trend.data)
    pieOption.value = buildPieOption(perf.data)
  } catch {
    /* silent */
  } finally {
    loading.value = false
  }
}

onMounted(load)

const docTypeMap: Record<string, string> = { PR: '请购单', PO: '采购订单' }

function goApproval(_item: PendingItem) {
  router.push({ path: '/approval/list' })
}

const shortcuts = [
  { label: '新建请购', path: '/pr/new', color: '#409EFF' },
  { label: '新建订单', path: '/purchase/orders/new', color: '#67C23A' },
  /** 收货单列表内再「新建收货」（非宁波）；宁波走 U9 同步，避免误进 /receipts/new 被拦截 */
  { label: '收货单', path: '/purchase/receipts', color: '#E6A23C' },
  { label: '绩效考核', path: '/perf/evaluations', color: '#F56C6C' },
]
</script>

<template>
  <div class="dashboard">
    <div class="dash-header">
      <h2>工作台</h2>
      <span class="greeting">{{ auth.displayName }}，欢迎回来</span>
    </div>

    <el-alert
      v-if="stats && stats.unreadUserNotifications > 0"
      type="info"
      show-icon
      :closable="false"
      class="dash-notif-alert"
    >
      <template #title>
        <span>您有 <strong>{{ stats.unreadUserNotifications }}</strong> 条未读站内消息</span>
      </template>
      <template #default>
        <router-link to="/notifications" class="dash-notif-link">
          <el-icon><Bell /></el-icon>
          前往消息中心
        </router-link>
      </template>
    </el-alert>

    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card--blue">
          <div class="stat-icon"><el-icon :size="28"><Tickets /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ stats?.pendingApprovals ?? '-' }}</div>
            <div class="stat-label">待审批</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card--green">
          <div class="stat-icon"><el-icon :size="28"><Money /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ stats ? Number(stats.monthPoAmount).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 0 }) : '-' }}</div>
            <div class="stat-label">本月订单金额</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card--orange">
          <div class="stat-icon"><el-icon :size="28"><Box /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ stats?.pendingReceiveLines ?? '-' }}</div>
            <div class="stat-label">待收货行</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card--red">
          <div class="stat-icon"><el-icon :size="28"><Document /></el-icon></div>
          <div class="stat-body">
            <div class="stat-value">{{ stats?.pendingInvoices ?? '-' }}</div>
            <div class="stat-label">待处理发票</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :sm="16">
        <el-card shadow="never" class="dash-card">
          <template #header><span class="card-title">我的待办</span></template>
          <el-table v-if="pending.length" :data="pending" size="small" :show-header="false" @row-click="goApproval">
            <el-table-column width="80">
              <template #default="{ row }">
                <el-tag size="small">{{ docTypeMap[row.docType] || row.docType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="docNo" />
            <el-table-column width="130" align="right">
              <template #default="{ row }">
                <span style="color: #409EFF">¥ {{ Number(row.amount).toLocaleString() }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="EMPTY_DASHBOARD_DESC" :image-size="60">
            <template #default>
              <p class="dash-empty-hint">{{ EMPTY_DASHBOARD_HINT }}</p>
            </template>
          </el-empty>
        </el-card>
      </el-col>
      <el-col :sm="8">
        <el-card shadow="never" class="dash-card shortcut-card">
          <template #header><span class="card-title">快捷入口</span></template>
          <div class="shortcut-grid">
            <router-link v-for="s in shortcuts" :key="s.path" :to="s.path" class="shortcut-item">
              <div class="shortcut-dot" :style="{ background: s.color }"></div>
              <span>{{ s.label }}</span>
            </router-link>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :sm="14">
        <el-card shadow="never" class="dash-card">
          <template #header><span class="card-title">采购金额趋势（近6月）</span></template>
          <v-chart :option="barOption" style="height: 280px" autoresize />
        </el-card>
      </el-col>
      <el-col :sm="10">
        <el-card shadow="never" class="dash-card">
          <template #header><span class="card-title">供应商绩效分布</span></template>
          <v-chart :option="pieOption" style="height: 280px" autoresize />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard { padding: 0; }
.dash-header {
  display: flex; align-items: baseline; gap: 16px; margin-bottom: 20px;
  padding-bottom: 16px; border-bottom: 1px solid var(--el-border-color-lighter);
}
.dash-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #111827; }
.greeting { font-size: 13px; color: #6b7280; }

.dash-notif-alert { margin-bottom: 16px; border-radius: 8px; }
.dash-notif-link {
  display: inline-flex; align-items: center; gap: 6px;
  margin-top: 6px; font-size: 13px; color: var(--el-color-primary); text-decoration: none;
}
.dash-notif-link:hover { text-decoration: underline; }

.stat-row { margin-bottom: 16px; }
.stat-card {
  display: flex; align-items: center; gap: 14px; padding: 18px 20px;
  border-radius: 8px; background: #fff; border: 1px solid var(--el-border-color-lighter);
  transition: box-shadow .2s;
}
.stat-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.08); }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; }
.stat-card--blue .stat-icon { background: #409EFF; }
.stat-card--green .stat-icon { background: #67C23A; }
.stat-card--orange .stat-icon { background: #E6A23C; }
.stat-card--red .stat-icon { background: #F56C6C; }
.stat-value { font-size: 22px; font-weight: 700; line-height: 1.2; color: #111827; }
.stat-label { font-size: 12px; color: #6b7280; margin-top: 2px; }

.section-row { margin-bottom: 16px; }
.dash-card { border-radius: 8px; }
.card-title { font-weight: 600; font-size: 14px; }

.shortcut-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.shortcut-item {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px;
  border-radius: 6px; background: #f9fafb; text-decoration: none; color: #374151;
  font-size: 13px; transition: background .2s;
}
.shortcut-item:hover { background: #f0f5ff; }
.shortcut-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dash-empty-hint {
  margin: 0 auto;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
