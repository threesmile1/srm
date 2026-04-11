<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import {
  executionApi,
  downloadArrayBuffer,
  type GrDetail,
  type GrSummary,
  type OpenPoAsnReceiptHint,
} from '../../api/execution'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'gr-list')
const rows = ref<GrSummary[]>([])
/** 已有 ASN、本组织下尚未创建任何收货单的订单 */
const openPoHints = ref<OpenPoAsnReceiptHint[]>([])

/**
 * 全部：当前采购组织下全部收货单
 * 待收货的发货通知：订单有已提交发货通知且尚未收清；含「仅有发货通知、尚未建收货单」的待办行
 */
const listTab = ref<'all' | 'waitReceive'>('all')

type GrListRow =
  | { rowKind: 'GR'; gr: GrSummary }
  | { rowKind: 'OPEN_PO'; open: OpenPoAsnReceiptHint }

function openQtyOnPo(r: GrSummary): number {
  const q = r.pendingReceiptQty
  if (q == null || String(q).trim() === '') return 0
  const n = parseFloat(String(q))
  return Number.isFinite(n) ? n : 0
}

function matchesWaitReceive(gr: GrSummary): boolean {
  if (openQtyOnPo(gr) <= 0) return false
  return gr.hasAsnShipment === true || gr.purchaseOrderHasSubmittedAsn === true
}

const displayRows = computed((): GrListRow[] => {
  if (listTab.value === 'all') {
    return rows.value.map((gr) => ({ rowKind: 'GR' as const, gr }))
  }
  const openPart: GrListRow[] = openPoHints.value.map((open) => ({ rowKind: 'OPEN_PO', open }))
  const grPart: GrListRow[] = rows.value.filter(matchesWaitReceive).map((gr) => ({ rowKind: 'GR', gr }))
  return [...openPart, ...grPart]
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
  const [r, o] = await Promise.all([
    executionApi.listGoodsReceipts(orgId.value),
    executionApi.listPendingOpenPoWithAsn(orgId.value),
  ])
  rows.value = r.data
  openPoHints.value = o.data
}

watch(orgId, () => {
  loadRows()
})

onMounted(async () => {
  await loadOrgs()
  await loadRows()
})

async function openDetail(gr: GrSummary) {
  const res = await executionApi.getGoodsReceipt(gr.id)
  detail.value = res.data
  drawer.value = true
}

function onRowDblclick(row: GrListRow) {
  if (row.rowKind === 'GR') openDetail(row.gr)
}

function rowSelectable(row: GrListRow) {
  return row.rowKind === 'GR'
}

async function exportSelected() {
  const sel: GrListRow[] = tableRef.value?.getSelectionRows?.() ?? []
  const ids = sel.filter((x): x is Extract<GrListRow, { rowKind: 'GR' }> => x.rowKind === 'GR').map((x) => x.gr.id)
  if (!ids.length) {
    ElMessage.warning('请选择要导出的收货单')
    return
  }
  try {
    const r = await executionApi.exportGoodsReceipts(ids)
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
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="待收货的发货通知" name="waitReceive" />
    </el-tabs>
    <p v-if="listTab === 'waitReceive'" class="tab-hint">
      含「仅有发货通知、尚未登记收货单」的待办；已有收货单且关联 ASN 或订单侧有发货通知的也会列出。
    </p>
    <el-table
      ref="tableRef"
      :data="displayRows"
      stripe
      :row-key="(row: GrListRow) => (row.rowKind === 'OPEN_PO' ? 'o-' + row.open.purchaseOrderId : 'g-' + row.gr.id)"
      :selectable="rowSelectable"
      @row-dblclick="onRowDblclick"
    >
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column type="selection" width="42" />
      <el-table-column label="收货单号" width="160">
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'" class="muted">（待创建）</span>
          <span v-else>{{ row.gr.grNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="采购订单" width="160">
        <template #default="{ row }: { row: GrListRow }">
          {{ row.rowKind === 'OPEN_PO' ? row.open.poNo : row.gr.poNo }}
        </template>
      </el-table-column>
      <el-table-column label="发货通知" width="150">
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'">{{ row.open.asnNo }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="仓库" width="100">
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'" class="muted">—</span>
          <span v-else>{{ row.gr.warehouseCode }}</span>
        </template>
      </el-table-column>
      <el-table-column label="收货日期" width="120">
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'" class="muted">—</span>
          <span v-else>{{ row.gr.receiptDate }}</span>
        </template>
      </el-table-column>
      <el-table-column label="尚未收清数量" min-width="120">
        <template #default="{ row }: { row: GrListRow }">
          {{ row.rowKind === 'OPEN_PO' ? row.open.pendingReceiptQty : row.gr.pendingReceiptQty }}
        </template>
      </el-table-column>
      <el-table-column label="导出状态" width="110">
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'" class="muted">—</span>
          <span v-else>{{ row.gr.exportStatus }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }: { row: GrListRow }">
          <router-link
            v-if="row.rowKind === 'OPEN_PO' && orgId != null"
            class="link-go"
            :to="{
              path: '/purchase/receipts/new',
              query: { procurementOrgId: String(orgId), poId: String(row.open.purchaseOrderId) },
            }"
          >
            去收货
          </router-link>
          <el-button v-else-if="row.rowKind === 'GR'" link type="primary" @click="openDetail(row.gr)">明细</el-button>
          <span v-else class="muted">—</span>
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
.tab-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 10px;
  line-height: 1.5;
}
.muted {
  color: var(--el-text-color-placeholder);
}
.link-go {
  color: var(--el-color-primary);
  font-size: var(--el-font-size-base);
}
</style>
