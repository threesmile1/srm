<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'
import { portalReconApi, type ReconSummary } from '../api/invoice'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const rows = ref<ReconSummary[]>([])

type OrgOption = { id: number; code: string; name: string }
const orgs = ref<OrgOption[]>([])
const createOpen = ref(false)
const createSubmitting = ref(false)
const createForm = ref({
  procurementOrgId: null as number | null,
  periodFrom: '',
  periodTo: '',
  remark: '',
})

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

async function loadOrgs() {
  const ledgers = (await api.get('/api/v1/ledgers')).data as { id: number }[]
  if (!ledgers.length) return
  const ou = (await api.get(`/api/v1/ledgers/${ledgers[0].id}/org-units`)).data as (OrgOption & {
    orgType: string
  })[]
  orgs.value = ou.filter((o) => o.orgType === 'PROCUREMENT')
  if (orgs.value.length && createForm.value.procurementOrgId == null) {
    createForm.value.procurementOrgId = orgs.value[0].id
  }
}

async function load() {
  rows.value = (await portalReconApi.list()).data
}

onMounted(async () => {
  await load()
  await loadOrgs()
})

async function openCreate() {
  if (!orgs.value.length) await loadOrgs()
  if (!orgs.value.length) {
    ElMessage.warning('未获取到采购组织，请稍后重试')
    return
  }
  createForm.value = {
    procurementOrgId: orgs.value[0].id,
    periodFrom: '',
    periodTo: '',
    remark: '',
  }
  createOpen.value = true
}

async function submitCreate() {
  if (createForm.value.procurementOrgId == null) {
    ElMessage.warning('请选择采购组织')
    return
  }
  if (!createForm.value.periodFrom || !createForm.value.periodTo) {
    ElMessage.warning('请填写对账期间起止')
    return
  }
  createSubmitting.value = true
  try {
    await portalReconApi.create({
      procurementOrgId: createForm.value.procurementOrgId,
      periodFrom: createForm.value.periodFrom,
      periodTo: createForm.value.periodTo,
      remark: createForm.value.remark.trim() || undefined,
    })
    ElMessage.success('已发起对账，请等待采购核对')
    createOpen.value = false
    await load()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '发起失败')
  } finally {
    createSubmitting.value = false
  }
}

async function supplierConfirm(row: ReconSummary) {
  try {
    await portalReconApi.supplierConfirm(row.id)
    ElMessage.success('已确认对账单')
    await load()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
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
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '提交失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">对账</span>
      <el-button type="primary" @click="openCreate">发起对账</el-button>
      <span class="sub">
        甄云类流程：通常由<strong>供应商在月末发起对账</strong>，采购核对；采购也可在管理端生成对账单，则您需对采购出具的账单做「确认」或「异议」。
      </span>
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

    <el-dialog v-model="createOpen" title="发起对账" width="520px" destroy-on-close>
      <p class="dialog-hint">按甄云类财务协同：选择采购组织与对账期间，系统将汇总该期间 PO/收货/已确认发票金额供采购核对。</p>
      <el-form label-width="100px">
        <el-form-item label="采购组织" required>
          <el-select v-model="createForm.procurementOrgId" style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间起" required>
          <el-date-picker v-model="createForm.periodFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="期间止" required>
          <el-date-picker v-model="createForm.periodTo" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="选填" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

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
.sub { font-size: 13px; color: var(--el-text-color-secondary); max-width: 720px; line-height: 1.5; }
.muted { font-size: 13px; color: var(--el-text-color-secondary); }
.by { color: var(--el-text-color-secondary); font-size: 12px; }
.dialog-hint { margin: 0 0 12px; font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.45; }
</style>
