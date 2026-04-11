<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { executionApi, downloadArrayBuffer, type GrDetail, type GrSummary } from '../../api/execution'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'gr-list')
const rows = ref<GrSummary[]>([])
/**
 * 已发货通知待收货：存在 ASN 发货通知行，且关联订单仍有未收清数量
 * 尚未收清订单：无 ASN 关联行，但关联订单仍有未收清数量（如线下/无通知收货）
 * 已收清订单：关联采购订单已全部收清（尚未收清数量 ≤ 0）
 */
const listTab = ref<'waitReceive' | 'poOpen' | 'poCleared'>('waitReceive')

function openQtyOnPo(r: GrSummary): number {
  const q = r.pendingReceiptQty
  if (q == null || String(q).trim() === '') return 0
  const n = parseFloat(String(q))
  return Number.isFinite(n) ? n : 0
}

function hasAsn(r: GrSummary): boolean {
  return r.hasAsnShipment === true
}

const displayRows = computed(() => {
  const all = rows.value
  switch (listTab.value) {
    case 'waitReceive':
      return all.filter((r) => openQtyOnPo(r) > 0 && hasAsn(r))
    case 'poOpen':
      return all.filter((r) => openQtyOnPo(r) > 0 && !hasAsn(r))
    case 'poCleared':
      return all.filter((r) => openQtyOnPo(r) <= 0)
    default:
      return all
  }
})
const tableRef = ref()
const drawer = ref(false)
const detail = ref<GrDetail | null>(null)

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadRows() {
  if (orgId.value == null) return
  const r = await executionApi.listGoodsReceipts(orgId.value)
  rows.value = r.data
}

watch(orgId, () => {
  loadRows()
})

onMounted(async () => {
  await loadOrgs()
  await loadRows()
})

async function openDetail(row: GrSummary) {
  const r = await executionApi.getGoodsReceipt(row.id)
  detail.value = r.data
  drawer.value = true
}

async function exportSelected() {
  const sel: GrSummary[] = tableRef.value?.getSelectionRows?.() ?? []
  if (!sel.length) {
    ElMessage.warning('请选择要导出的收货单')
    return
  }
  try {
    const r = await executionApi.exportGoodsReceipts(sel.map((x) => x.id))
    downloadArrayBuffer(r.data, 'srm-goods-receipts.xlsx')
    ElMessage.success('已下载')
    await loadRows()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '导出失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">收货单</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="$router.push('/purchase/receipts/new')">新建收货</el-button>
      <el-button @click="exportSelected">导出选中（U9）</el-button>
    </div>
    <el-tabs v-model="listTab" class="list-tabs">
      <el-tab-pane label="已发货通知待收货" name="waitReceive" />
      <el-tab-pane label="尚未收清订单" name="poOpen" />
      <el-tab-pane label="已收清订单" name="poCleared" />
    </el-tabs>
    <el-table ref="tableRef" :data="displayRows" stripe @row-dblclick="(row: GrSummary) => openDetail(row)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column type="selection" width="42" />
      <el-table-column prop="grNo" label="收货单号" width="160" />
      <el-table-column prop="poNo" label="采购订单" width="160" />
      <el-table-column prop="warehouseCode" label="仓库" width="100" />
      <el-table-column prop="receiptDate" label="收货日期" width="120" />
      <el-table-column prop="pendingReceiptQty" label="尚未收清数量" min-width="120" />
      <el-table-column prop="exportStatus" label="导出状态" width="110" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawer" title="收货单明细" size="520px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="单号">{{ detail.grNo }}</el-descriptions-item>
          <el-descriptions-item label="订单">{{ detail.poNo }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detail.warehouseCode }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ detail.receiptDate }}</el-descriptions-item>
          <el-descriptions-item label="导出">{{ detail.exportStatus }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.lines" size="small" stripe style="margin-top: 12px">
          <el-table-column prop="lineNo" label="行" width="50" />
          <el-table-column prop="materialCode" label="物料" width="100" />
          <el-table-column prop="receivedQty" label="数量" width="90" />
          <el-table-column prop="asnNo" label="ASN" width="120" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.list-tabs {
  margin-bottom: 8px;
}
.list-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
</style>
