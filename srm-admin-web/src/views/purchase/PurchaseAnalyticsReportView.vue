<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import {
  executionApi,
  type DeliveryAchievement,
  type PriceAnalysisRow,
  type ReportMonthAmount,
  type SupplierShareRow,
} from '../../api/execution'
import { EMPTY_LIST_DESC, EMPTY_LIST_HINT } from '../../constants/emptyCopy'
import DataTableEmpty from '../../components/DataTableEmpty.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
  LineChart,
  PieChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
])

function todayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function daysAgoStr(days: number) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'report-analytics')
const trendMonths = ref(12)
const dateRange = ref<[string, string]>([daysAgoStr(90), todayStr()])

const trendData = ref<ReportMonthAmount[]>([])
const shareRaw = ref<SupplierShareRow[]>([])
const delivery = ref<DeliveryAchievement | null>(null)
const priceRows = ref<PriceAnalysisRow[]>([])
const loading = ref(false)

const lineOption = shallowRef<Record<string, unknown>>({})
const pieOption = shallowRef<Record<string, unknown>>({})
const barOption = shallowRef<Record<string, unknown>>({})

const sharePieSlices = computed(() => {
  const list = shareRaw.value
  if (!list.length) return []
  const topN = 10
  if (list.length <= topN) {
    return list.map((r) => ({
      name: `${r.supplierCode} ${r.supplierName}`.trim(),
      value: r.amount,
      pct: r.sharePercent,
    }))
  }
  const head = list.slice(0, topN)
  const restAmt = list.slice(topN).reduce((s, r) => s + r.amount, 0)
  const restPct = list.slice(topN).reduce((s, r) => s + r.sharePercent, 0)
  return [
    ...head.map((r) => ({
      name: `${r.supplierCode} ${r.supplierName}`.trim(),
      value: r.amount,
      pct: r.sharePercent,
    })),
    { name: '其他', value: restAmt, pct: restPct },
  ]
})

function buildLineOption(data: ReportMonthAmount[]) {
  return {
    tooltip: {
      trigger: 'axis' as const,
      valueFormatter: (v: number) =>
        `¥ ${Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`,
    },
    grid: { left: 56, right: 24, top: 28, bottom: 28 },
    xAxis: { type: 'category' as const, data: data.map((d) => d.month), boundaryGap: false },
    yAxis: { type: 'value' as const, scale: true },
    series: [
      {
        type: 'line' as const,
        smooth: true,
        symbolSize: 6,
        data: data.map((d) => d.amount),
        areaStyle: { color: 'rgba(64, 158, 255, 0.12)' },
        lineStyle: { color: '#409EFF', width: 2 },
        itemStyle: { color: '#409EFF' },
      },
    ],
  }
}

function buildPieOption(slices: { name: string; value: number; pct: number }[]) {
  return {
    tooltip: {
      trigger: 'item' as const,
      formatter: (p: { name: string; value: number; percent: number }) =>
        `${p.name}<br/>金额 ¥ ${Number(p.value).toLocaleString()}（${p.percent.toFixed(1)}%）`,
    },
    legend: { type: 'scroll' as const, bottom: 0 },
    series: [
      {
        type: 'pie' as const,
        radius: ['36%', '62%'],
        label: { formatter: '{b}\n{d}%' },
        data: slices.map((s, i) => ({
          name: s.name.length > 18 ? `${s.name.slice(0, 16)}…` : s.name,
          value: s.value,
          itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] },
        })),
      },
    ],
  }
}

const PIE_COLORS = [
  '#409EFF',
  '#67C23A',
  '#E6A23C',
  '#F56C6C',
  '#909399',
  '#00b4d8',
  '#9b59b6',
  '#1abc9c',
  '#e67e22',
  '#34495e',
  '#95a5a6',
]

function buildVolatilityBar(rows: PriceAnalysisRow[]) {
  const top = rows.slice(0, 12)
  return {
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
    },
    grid: { left: 120, right: 32, top: 16, bottom: 24 },
    xAxis: { type: 'value' as const, name: '波动率 %' },
    yAxis: {
      type: 'category' as const,
      data: top.map((r) => `${r.materialCode}`).reverse(),
      axisLabel: { width: 110, overflow: 'truncate' as const },
    },
    series: [
      {
        type: 'bar' as const,
        data: top.map((r) => r.volatilityPercent).reverse(),
        itemStyle: { color: '#67C23A', borderRadius: [0, 4, 4, 0] },
        barMaxWidth: 22,
      },
    ],
  }
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadAll() {
  if (orgId.value == null) return
  loading.value = true
  try {
    const [from, to] = dateRange.value
    const [t, s, d, p] = await Promise.all([
      executionApi.purchaseAmountTrend(orgId.value, trendMonths.value),
      executionApi.supplierShare(orgId.value, from, to),
      executionApi.deliveryAchievement(orgId.value),
      executionApi.priceAnalysis(orgId.value, from, 25),
    ])
    trendData.value = t.data
    shareRaw.value = s.data
    delivery.value = d.data
    priceRows.value = p.data
    lineOption.value = buildLineOption(t.data)
    pieOption.value = buildPieOption(sharePieSlices.value)
    barOption.value = buildVolatilityBar(p.data)
  } finally {
    loading.value = false
  }
}

watch(
  [orgId, trendMonths, dateRange],
  () => {
    loadAll()
  },
  { deep: true, immediate: true },
)

onMounted(async () => {
  await loadOrgs()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="toolbar">
      <span class="title">采购分析报表</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-select v-model="trendMonths" style="width: 120px">
        <el-option :value="6" label="趋势6个月" />
        <el-option :value="12" label="趋势12个月" />
        <el-option :value="18" label="趋势18个月" />
        <el-option :value="24" label="趋势24个月" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始"
        end-placeholder="结束"
        value-format="YYYY-MM-DD"
        style="width: 280px"
      />
    </div>

    <p class="hint">
      采购金额趋势按订单创建月汇总；供应商份额与价格波动按所选日期范围内订单行统计；交期达成率基于已收满行，以承诺/要求交期对比末次收货日。
    </p>

    <el-row :gutter="16" class="row-block">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="card">
          <template #header><span class="card-title">采购金额趋势</span></template>
          <v-chart :option="lineOption" style="height: 300px" autoresize />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="card">
          <template #header><span class="card-title">供应商采购份额</span></template>
          <v-chart :option="pieOption" style="height: 300px" autoresize />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-block">
      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="card">
          <template #header><span class="card-title">交期达成（已收满行）</span></template>
          <div v-if="delivery" class="delivery-grid">
            <div class="delivery-item">
              <div class="delivery-value success">{{ delivery.completedOnTime }}</div>
              <div class="delivery-label">按期完成行</div>
            </div>
            <div class="delivery-item">
              <div class="delivery-value warn">{{ delivery.completedLate }}</div>
              <div class="delivery-label">延期完成行</div>
            </div>
            <div class="delivery-item">
              <div class="delivery-value muted">{{ delivery.openWithDueDate }}</div>
              <div class="delivery-label">未交行（有交期）</div>
            </div>
            <div class="delivery-item wide">
              <div class="delivery-value primary">
                {{ delivery.onTimeRatePercent.toFixed(1) }}%
              </div>
              <div class="delivery-label">达成率（按期 / 已完结）</div>
            </div>
          </div>
          <el-empty v-else :description="EMPTY_LIST_DESC" :image-size="48">
            <template #default>
              <p class="analytics-empty-hint">{{ EMPTY_LIST_HINT }}</p>
            </template>
          </el-empty>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="card">
          <template #header>
            <span class="card-title">价格波动（TOP 物料，区间内多笔订单）</span>
          </template>
          <v-chart :option="barOption" style="height: 300px" autoresize />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="card">
      <template #header><span class="card-title">价格分析明细</span></template>
      <el-table :data="priceRows" stripe size="small">
        <template #empty>
          <DataTableEmpty />
        </template>
        <el-table-column prop="materialCode" label="物料编码" width="120" />
        <el-table-column prop="materialName" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="minUnitPrice" label="最低价" width="100" align="right">
          <template #default="{ row }">{{ Number(row.minUnitPrice).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column prop="maxUnitPrice" label="最高价" width="100" align="right">
          <template #default="{ row }">{{ Number(row.maxUnitPrice).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column prop="avgUnitPrice" label="均价" width="100" align="right">
          <template #default="{ row }">{{ Number(row.avgUnitPrice).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column prop="volatilityPercent" label="波动率%" width="100" align="right">
          <template #default="{ row }">{{ Number(row.volatilityPercent).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="lineCount" label="行数" width="72" align="right" />
        <el-table-column prop="totalAmount" label="金额" width="120" align="right">
          <template #default="{ row }">
            ¥ {{ Number(row.totalAmount).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin-right: 8px;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 16px;
  line-height: 1.5;
}
.row-block {
  margin-bottom: 16px;
}
.card {
  border-radius: 8px;
}
.card-title {
  font-weight: 600;
  font-size: 14px;
}
.delivery-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 8px 0;
}
.delivery-item.wide {
  grid-column: 1 / -1;
  text-align: center;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.delivery-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}
.delivery-value.success {
  color: #67c23a;
}
.delivery-value.warn {
  color: #e6a23c;
}
.delivery-value.muted {
  color: #909399;
}
.delivery-value.primary {
  color: #409eff;
  font-size: 26px;
}
.delivery-label {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
.analytics-empty-hint {
  margin: 0 auto;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
