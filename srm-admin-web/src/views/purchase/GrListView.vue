<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import {
  executionApi,
  downloadArrayBuffer,
  type GrDetail,
  type GrSummary,
  type OpenPoAsnReceiptHint,
  type U9GoodsReceiptSyncResult,
} from '../../api/execution'
import { approvalApi } from '../../api/approval'
import { useRouter } from 'vue-router'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'gr-list')
const router = useRouter()
const rows = ref<GrSummary[]>([])
/** 已有 ASN、本组织下尚未创建任何收货单的订单 */
const openPoHints = ref<OpenPoAsnReceiptHint[]>([])
const u9GrSyncing = ref(false)
const listLoading = ref(false)

const page = ref(0)
const size = ref(20)
const total = ref(0)

function isNingboProcurementOrg(o: OrgUnit | null | undefined): boolean {
  if (!o) return false
  const code = (o.code ?? '').trim().toUpperCase()
  const name = (o.name ?? '').trim()
  const u9 = (o.u9OrgCode ?? '').trim()
  return code === 'NB' || name === '宁波公司' || u9 === '1001711275375071'
}

const currentProcurementOrg = computed(() => orgs.value.find((x) => x.id === orgId.value) ?? null)

/**
 * 待收货的发货通知（默认）：宁波仅「已提交 ASN 且客服尚未确认通过」；其他组织仍为原规则（有 ASN 且未收清）
 * 全部：当前采购组织下全部收货单
 */
const listTab = ref<'all' | 'waitReceive'>('waitReceive')

const waitReceiveTabHint = computed(() =>
  isNingboProcurementOrg(currentProcurementOrg.value)
    ? '宁波公司：仅列出存在已提交发货通知、且客服尚未确认通过（含待审/已驳回等）的记录；客服确认通过后不再出现在本页签。'
    : '含「仅有发货通知、尚未登记收货单」的待办；已有收货单且关联 ASN 或订单侧有发货通知的也会列出。',
)

type GrListRow =
  | { rowKind: 'GR'; gr: GrSummary }
  | { rowKind: 'OPEN_PO'; open: OpenPoAsnReceiptHint }

function grStatusLabel(s: string | undefined): string {
  if (s === 'PENDING_APPROVAL') return '待客服审核'
  if (s === 'REJECTED') return '已驳回'
  if (s === 'APPROVED') return '已通过'
  return s || '—'
}

const displayRows = computed((): GrListRow[] => {
  if (listTab.value === 'all') {
    return rows.value.map((gr) => ({ rowKind: 'GR' as const, gr }))
  }
  const openPart: GrListRow[] =
    page.value === 0 ? openPoHints.value.map((open) => ({ rowKind: 'OPEN_PO' as const, open })) : []
  const grPart: GrListRow[] = rows.value.map((gr) => ({ rowKind: 'GR' as const, gr }))
  return [...openPart, ...grPart]
})

const tableRef = ref()
const drawer = ref(false)
const detail = ref<GrDetail | null>(null)

/** 明细抽屉顶部：从行上的 ASN 去重拼接 */
const detailAsnSummary = computed(() => {
  const d = detail.value
  if (!d?.lines?.length) return ''
  const set = new Set<string>()
  for (const ln of d.lines) {
    if (ln.asnNo) set.add(ln.asnNo)
  }
  return set.size ? [...set].join(', ') : ''
})

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadRows() {
  if (orgId.value == null) return
  listLoading.value = true
  try {
    const waitReceiveOnly = listTab.value === 'waitReceive'
    const [r, o] = await Promise.all([
      executionApi.listGoodsReceiptsPaged(orgId.value, page.value, size.value, waitReceiveOnly),
      waitReceiveOnly && page.value === 0 ? executionApi.listPendingOpenPoWithAsn(orgId.value) : Promise.resolve({ data: [] }),
    ])
    rows.value = r.data.content ?? []
    total.value = r.data.totalElements ?? 0
    openPoHints.value = o.data ?? []
  } finally {
    listLoading.value = false
  }
}

watch(orgId, () => {
  page.value = 0
  loadRows()
})

watch(listTab, () => {
  page.value = 0
  loadRows()
})

watch([page, size], () => {
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

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** U9 收货同步结果弹窗：汇总表 + 说明 + 明细列表，便于阅读 */
function buildU9GrSyncResultHtml(d: U9GoodsReceiptSyncResult): string {
  const rowCount = d.rowCount ?? 0
  const dropped = d.droppedUnmappedRows ?? 0
  const groups = d.groupsTotal ?? 0
  const created = d.created ?? 0
  const updated = d.updatedStatusOnly ?? 0
  const skipNb = d.skippedNonNingbo ?? 0
  const skipped = d.skipped ?? 0
  const errors = d.errors ?? []
  const errN = errors.length

  const statRows: [string, string][] = [
    ['帆软返回行数', String(rowCount)],
    ['按收货单号归组数', String(groups)],
    ['新建收货单', String(created)],
    ['仅更新 U9 状态', String(updated)],
    ['未参与归组行', dropped > 0 ? String(dropped) : '0'],
    ['同步失败/跳过组', skipped > 0 ? String(skipped) : '0'],
    ['非宁波组织跳过', String(skipNb)],
    ['错误/提示条数', errN > 0 ? String(errN) : '0'],
  ]

  let html = '<div class="u9-gr-sync-body">'
  html += '<p class="u9-gr-sync-lead">以下为本次同步统计；若存在失败或提示，请查看下方明细。</p>'
  html += '<table class="u9-gr-sync-stats" cellspacing="0">'
  for (const [k, v] of statRows) {
    html += `<tr><th scope="row">${escapeHtml(k)}</th><td>${escapeHtml(v)}</td></tr>`
  }
  html += '</table>'

  if (dropped > 0) {
    html +=
      '<section class="u9-gr-sync-note">' +
      `<p><strong>未归组 ${dropped} 行</strong>：缺少「收货单号」或列名与约定不一致；组织需能匹配宁波采购组织（u9_org_code / 名称 / code）。</p>` +
      '</section>'
  }

  const totalWritten = created + updated
  if (totalWritten === 0 && rowCount > 0 && errN === 0 && dropped === 0 && skipNb > 0) {
    html +=
      '<section class="u9-gr-sync-note">' +
      '<p>本次未写入任何收货单：帆软数据可能均不属于当前所选宁波采购组织（见「非宁波组织跳过」）。</p>' +
      '</section>'
  }

  if (errN > 0) {
    const show = errors.slice(0, 40)
    html += '<h4 class="u9-gr-sync-subh">提示 / 失败明细</h4><ol class="u9-gr-sync-err">'
    for (const line of show) {
      html += `<li>${escapeHtml(line)}</li>`
    }
    html += '</ol>'
    if (errN > 40) {
      html += `<p class="u9-gr-sync-more">… 共 ${errN} 条，以上仅展示前 40 条</p>`
    }
  }

  html += '</div>'
  return html
}

async function syncGoodsReceiptsFromU9() {
  if (!isNingboProcurementOrg(currentProcurementOrg.value)) {
    ElMessage.warning('请先在上方选择「宁波公司」采购组织')
    return
  }
  if (orgId.value == null) {
    ElMessage.warning('请先选择采购组织')
    return
  }
  u9GrSyncing.value = true
  try {
    const r = await executionApi.syncGoodsReceiptsFromU9(orgId.value)
    const errN = r.data.errors?.length ?? 0
    const dropped = r.data.droppedUnmappedRows ?? 0
    const created = r.data.created ?? 0
    const updated = r.data.updatedStatusOnly ?? 0
    const rowCount = r.data.rowCount ?? 0
    const skipNb = r.data.skippedNonNingbo ?? 0
    const totalWritten = created + updated
    const syncOk =
      errN === 0 && dropped === 0 && (totalWritten > 0 || rowCount === 0)
    if (syncOk) {
      ElMessage.success(
        `U9 收货单同步完成：新建 ${created}，状态更新 ${updated}，帆软行 ${rowCount}（非宁波跳过 ${skipNb}）`,
      )
    } else {
      ElMessage.warning('同步已结束，部分未成功或需关注，请查看明细弹窗')
      await ElMessageBox.alert(buildU9GrSyncResultHtml(r.data), 'U9 收货单同步结果', {
        confirmButtonText: '关闭',
        type: 'warning',
        dangerouslyUseHTMLString: true,
        customClass: 'u9-gr-sync-result-dialog',
      })
    }
    await loadRows()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '同步失败')
  } finally {
    u9GrSyncing.value = false
  }
}

async function goCustomerServiceConfirm(open: OpenPoAsnReceiptHint) {
  try {
    const res = await approvalApi.getByDoc('ASN', open.asnNoticeId)
    const inst = res.data
    if (!inst) {
      ElMessage.warning(
        '未找到该发货通知的审批流程（仅宁波公司会在供应商提交后发起客服确认；若刚提交请稍后刷新）',
      )
      return
    }
    await router.push({ path: '/approval/list', query: { openInstanceId: String(inst.id) } })
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '打开审批失败')
  }
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
      <el-button
        v-if="!isNingboProcurementOrg(currentProcurementOrg)"
        type="primary"
        @click="$router.push('/purchase/receipts/new')"
      >
        新建收货
      </el-button>
      <el-button
        v-if="isNingboProcurementOrg(currentProcurementOrg)"
        type="primary"
        :loading="u9GrSyncing"
        @click="syncGoodsReceiptsFromU9"
      >
        从 U9 同步收货单（帆软）
      </el-button>
      <el-button @click="exportSelected">导出选中（U9）</el-button>
    </div>
    <el-tabs v-model="listTab" class="list-tabs">
      <el-tab-pane label="待收货的发货通知" name="waitReceive" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>
    <p v-if="listTab === 'waitReceive'" class="tab-hint">
      {{ waitReceiveTabHint }}
    </p>
    <el-table
      ref="tableRef"
      :data="displayRows"
      v-loading="listLoading"
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
      <el-table-column label="U9收货单号" width="160" show-overflow-tooltip>
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'" class="muted">—</span>
          <span v-else>{{ row.gr.u9DocNo?.trim() || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="采购订单" width="160">
        <template #default="{ row }: { row: GrListRow }">
          {{ row.rowKind === 'OPEN_PO' ? row.open.poNo : row.gr.poNo }}
        </template>
      </el-table-column>
      <el-table-column label="U9采购订单号" width="160" show-overflow-tooltip>
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'">{{ row.open.poU9DocNo?.trim() || '—' }}</span>
          <span v-else>{{ row.gr.poU9DocNo?.trim() || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发货通知" width="180" show-overflow-tooltip>
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'">{{ row.open.asnNo }}</span>
          <span v-else-if="row.gr.asnSummary">{{ row.gr.asnSummary }}</span>
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
      <el-table-column label="业务状态" width="120">
        <template #default="{ row }: { row: GrListRow }">
          <span v-if="row.rowKind === 'OPEN_PO'" class="muted">—</span>
          <span v-else>{{ grStatusLabel(row.gr.status) }}</span>
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
          <el-button
            v-if="row.rowKind === 'OPEN_PO' && orgId != null && isNingboProcurementOrg(currentProcurementOrg)"
            link
            type="primary"
            @click="goCustomerServiceConfirm(row.open)"
          >
            客服确认
          </el-button>
          <router-link
            v-else-if="row.rowKind === 'OPEN_PO' && orgId != null"
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

    <div style="display: flex; justify-content: flex-end; margin-top: 12px">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page + 1"
        :page-sizes="[10, 20, 50, 100]"
        @update:current-page="(p:number) => (page = p - 1)"
        @update:page-size="(s:number) => { size = s; page = 0 }"
      />
    </div>

    <el-drawer v-model="drawer" title="收货单明细" size="520px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="单号">{{ detail.grNo }}</el-descriptions-item>
          <el-descriptions-item label="U9收货单号">{{ detail.u9DocNo?.trim() || '—' }}</el-descriptions-item>
          <el-descriptions-item label="订单">{{ detail.poNo }}</el-descriptions-item>
          <el-descriptions-item label="U9采购订单号">{{ detail.poU9DocNo?.trim() || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发货通知">
            {{ detailAsnSummary || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detail.warehouseCode }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ detail.receiptDate }}</el-descriptions-item>
          <el-descriptions-item label="业务状态">{{ grStatusLabel(detail.status) }}</el-descriptions-item>
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

<style>
/* U9 收货同步结果弹窗（MessageBox 挂载在 body，需全局样式） */
.u9-gr-sync-result-dialog {
  max-width: min(720px, 92vw) !important;
}
.u9-gr-sync-result-dialog .el-message-box__message {
  max-height: min(420px, 65vh);
  overflow-y: auto;
  padding-right: 4px;
  text-align: left;
}
.u9-gr-sync-body {
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-primary);
}
.u9-gr-sync-lead {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
}
.u9-gr-sync-stats {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 14px;
  font-size: 13px;
}
.u9-gr-sync-stats th {
  text-align: left;
  font-weight: 600;
  padding: 6px 10px 6px 0;
  width: 42%;
  color: var(--el-text-color-regular);
  vertical-align: top;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.u9-gr-sync-stats td {
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-variant-numeric: tabular-nums;
}
.u9-gr-sync-note {
  margin: 12px 0;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
}
.u9-gr-sync-note p {
  margin: 0;
}
.u9-gr-sync-subh {
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: 600;
}
.u9-gr-sync-err {
  margin: 0;
  padding-left: 1.25rem;
}
.u9-gr-sync-err li {
  margin-bottom: 6px;
  word-break: break-word;
}
.u9-gr-sync-more {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
