<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalReconApi, type ReconSummary } from '../api/invoice'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const rows = ref<ReconSummary[]>([])

const statusMap: Record<string, string> = {
  PENDING_SUPPLIER: '待供应商确认',
  PENDING_PROCUREMENT: '待采购确认',
  CONFIRMED: '已确认',
  DISPUTED: '争议',
}

const disputeByMap: Record<string, string> = {
  SUPPLIER: '供应商',
  PROCUREMENT: '采购',
}

const disputeDialog = ref(false)
const disputeReason = ref('')
const activeId = ref<number | null>(null)

async function load() {
  rows.value = (await portalReconApi.list()).data
}

onMounted(load)

async function supplierConfirm(row: ReconSummary) {
  try {
    await portalReconApi.supplierConfirm(row.id)
    ElMessage.success('已确认对账单')
    await load()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e
      ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '确认失败')
  }
}

function openDispute(id: number) {
  activeId.value = id
  disputeReason.value = ''
  disputeDialog.value = true
}

async function submitDispute() {
  const id = activeId.value
  const t = disputeReason.value.trim()
  if (id == null || !t) {
    ElMessage.warning('请填写异议说明')
    return
  }
  try {
    await portalReconApi.supplierDispute(id, t)
    ElMessage.success('已提交异议')
    disputeDialog.value = false
    await load()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e
      ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '提交失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">对账</span>
      <span class="sub">采购生成对账单后，请确认或提出异议；采购驳回后会退回此处待再次确认</span>
    </div>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="reconNo" label="对账单号" width="180" />
      <el-table-column prop="periodFrom" label="开始" width="110" />
      <el-table-column prop="periodTo" label="结束" width="110" />
      <el-table-column prop="grAmount" label="收货金额" width="110" />
      <el-table-column prop="invoiceAmount" label="已确认票额" width="110" />
      <el-table-column prop="diffAmount" label="收货−票" width="100" />
      <el-table-column prop="procurementRejectReason" label="采购驳回" min-width="120" show-overflow-tooltip />
      <el-table-column label="异议" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.disputeReason">
            {{ row.disputeReason }}
            <span v-if="row.disputedBy" class="by">（{{ disputeByMap[row.disputedBy] || row.disputedBy }}）</span>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="差异" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.varianceAlert" type="warning" size="small">有差异</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'PENDING_PROCUREMENT' ? 'warning' : row.status === 'DISPUTED' ? 'danger' : 'info'"
            size="small"
          >
            {{ statusMap[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING_SUPPLIER'">
            <el-button link type="primary" @click="supplierConfirm(row)">供应商确认</el-button>
            <el-button link type="danger" @click="openDispute(row.id)">异议</el-button>
          </template>
          <span v-else-if="row.status === 'PENDING_PROCUREMENT'" class="muted">已提交，待采购处理</span>
          <span v-else-if="row.status === 'DISPUTED'" class="muted">争议处理中</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="disputeDialog" title="对账异议" width="520px" destroy-on-close @closed="disputeReason = ''">
      <p class="dialog-hint">请说明异议原因，提交后进入「争议」状态，由双方协商；采购可「重新打开」后继续确认流程。</p>
      <el-input v-model="disputeReason" type="textarea" :rows="4" placeholder="异议说明（必填）" maxlength="1000" show-word-limit />
      <template #footer>
        <el-button @click="disputeDialog = false">取消</el-button>
        <el-button type="danger" @click="submitDispute">提交异议</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: baseline; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 600; }
.sub { font-size: 13px; color: var(--el-text-color-secondary); }
.muted { font-size: 13px; color: var(--el-text-color-secondary); }
.by { color: var(--el-text-color-secondary); font-size: 12px; }
.dialog-hint { margin: 0 0 12px; font-size: 13px; color: var(--el-text-color-secondary); }
</style>
