<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { invoiceApi, type ReconSummary } from '../../api/invoice'
import { masterApi, type Supplier } from '../../api/master'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import { useAuthStore } from '../../stores/auth'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const auth = useAuthStore()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'recon-list', () => auth.user?.defaultProcurementOrgId ?? null)
const rows = ref<ReconSummary[]>([])
const suppliers = ref<Supplier[]>([])
const createOpen = ref(false)
const createSubmitting = ref(false)
const createForm = ref({
  supplierId: null as number | null,
  procurementOrgId: null as number | null,
  periodFrom: '',
  periodTo: '',
  remark: '',
})

const suppliersForOrg = computed(() => {
  const oid = orgId.value
  if (oid == null) return suppliers.value
  return suppliers.value.filter((s) => s.procurementOrgIds?.includes(oid))
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

const rejectDialog = ref(false)
const disputeDialog = ref(false)
const reasonText = ref('')
const activeReconId = ref<number | null>(null)

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadData() {
  if (orgId.value == null) return
  rows.value = (await invoiceApi.listRecon({ procurementOrgId: orgId.value })).data
}

watch(orgId, () => loadData())
onMounted(async () => { await loadOrgs(); await loadData() })

async function loadSuppliers() {
  suppliers.value = (await masterApi.listSuppliers()).data
}

async function openCreateRecon() {
  if (orgId.value == null) {
    ElMessage.warning('请先选择采购组织')
    return
  }
  if (!suppliers.value.length) await loadSuppliers()
  createForm.value = {
    supplierId: suppliersForOrg.value[0]?.id ?? null,
    procurementOrgId: orgId.value,
    periodFrom: '',
    periodTo: '',
    remark: '',
  }
  if (createForm.value.supplierId == null && suppliers.value.length) {
    createForm.value.supplierId = suppliers.value[0].id
  }
  createOpen.value = true
}

async function submitCreateRecon() {
  const f = createForm.value
  if (f.supplierId == null || f.procurementOrgId == null) {
    ElMessage.warning('请选择供应商与采购组织')
    return
  }
  if (!f.periodFrom || !f.periodTo) {
    ElMessage.warning('请填写对账期间起止')
    return
  }
  createSubmitting.value = true
  try {
    await invoiceApi.createRecon({
      supplierId: f.supplierId,
      procurementOrgId: f.procurementOrgId,
      periodFrom: f.periodFrom,
      periodTo: f.periodTo,
      remark: f.remark.trim() || undefined,
    })
    ElMessage.success('已生成对账单，待供应商确认')
    createOpen.value = false
    await loadData()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '创建失败')
  } finally {
    createSubmitting.value = false
  }
}

async function confirmRecon(id: number) {
  try {
    await invoiceApi.confirmRecon(id)
    ElMessage.success('采购已确认对账')
    await loadData()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '确认失败')
  }
}

function openReject(id: number) {
  activeReconId.value = id
  reasonText.value = ''
  rejectDialog.value = true
}

function openDispute(id: number) {
  activeReconId.value = id
  reasonText.value = ''
  disputeDialog.value = true
}

async function submitReject() {
  const id = activeReconId.value
  const t = reasonText.value.trim()
  if (id == null || !t) {
    ElMessage.warning('请填写驳回说明')
    return
  }
  try {
    await invoiceApi.procurementRejectRecon(id, t)
    ElMessage.success('已驳回至供应商')
    rejectDialog.value = false
    await loadData()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '驳回失败')
  }
}

async function submitDispute() {
  const id = activeReconId.value
  const t = reasonText.value.trim()
  if (id == null || !t) {
    ElMessage.warning('请填写异议说明')
    return
  }
  try {
    await invoiceApi.procurementDisputeRecon(id, t)
    ElMessage.success('已记录异议')
    disputeDialog.value = false
    await loadData()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '提交失败')
  }
}

async function reopenRecon(id: number) {
  try {
    await invoiceApi.reopenRecon(id)
    ElMessage.success('已重新打开，待供应商确认')
    await loadData()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">对账管理</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="openCreateRecon">新建对账</el-button>
      <span class="sub">甄云类：供应商也可在门户<strong>发起对账</strong>（直接进入待采购确认）；此处为采购代建，生成后待供应商确认。</span>
    </div>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="reconNo" label="对账单号" width="180" fixed />
      <el-table-column prop="supplierCode" label="供应商" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" width="160" />
      <el-table-column prop="periodFrom" label="开始日期" width="110" />
      <el-table-column prop="periodTo" label="结束日期" width="110" />
      <el-table-column prop="poAmount" label="PO金额" width="110">
        <template #header>
          <span>PO金额</span>
          <el-tooltip content="对账按收货月：与收货金额同为期间内入库执行额（收货行×订单单价）" placement="top">
            <span class="hint">?</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="grAmount" label="收货金额" width="110">
        <template #header>
          <span>收货金额</span>
          <el-tooltip content="收货单日期落在对账期间内的金额" placement="top">
            <span class="hint">?</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="invoiceAmount" label="已确认票额" width="120">
        <template #header>
          <span>已确认票额</span>
          <el-tooltip content="采购已确认的发票行中，所关联收货单的收货日期落在本期间内的行金额（无收货关联的行不计入）" placement="top">
            <span class="hint">?</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="diffAmount" label="收货−票" width="110" />
      <el-table-column prop="diffPoGrAmount" label="订单−收货" width="110">
        <template #header>
          <span>订单−收货</span>
          <el-tooltip content="对账按收货月时 PO 与收货同口径，本列通常为 0" placement="top">
            <span class="hint">?</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="差异" width="72">
        <template #default="{ row }">
          <el-tag v-if="row.varianceAlert" type="warning" size="small">有差异</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="procurementRejectReason" label="采购驳回" min-width="120" show-overflow-tooltip />
      <el-table-column label="异议" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.disputeReason">
            {{ row.disputeReason }}
            <span v-if="row.disputedBy" class="by">（{{ disputeByMap[row.disputedBy] || row.disputedBy }}）</span>
          </template>
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
      <el-table-column label="操作" width="248" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING_PROCUREMENT'">
            <el-button link type="primary" @click="confirmRecon(row.id)">采购确认</el-button>
            <el-button link type="warning" @click="openReject(row.id)">驳回</el-button>
            <el-button link type="danger" @click="openDispute(row.id)">异议</el-button>
          </template>
          <el-button v-else-if="row.status === 'DISPUTED'" link type="primary" @click="reopenRecon(row.id)">
            重新打开
          </el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="rejectDialog" title="驳回对账单" width="520px" destroy-on-close @closed="reasonText = ''">
      <p class="dialog-hint">驳回后退回「待供应商确认」，供应商需核对后再次确认。</p>
      <el-input v-model="reasonText" type="textarea" :rows="4" placeholder="驳回说明（必填）" maxlength="1000" show-word-limit />
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReject">确定驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createOpen" title="新建对账（采购代建）" width="560px" destroy-on-close>
      <p class="dialog-hint">对账按<strong>收货月</strong>汇总；发票统计为已确认且所关联收货日期落在期间内的发票行。对账单为「待供应商确认」状态。</p>
      <el-form label-width="100px">
        <el-form-item label="供应商" required>
          <el-select v-model="createForm.supplierId" filterable style="width: 100%" placeholder="选择供应商">
            <el-option
              v-for="s in (suppliersForOrg.length ? suppliersForOrg : suppliers)"
              :key="s.id"
              :label="`${s.code} ${s.name}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
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
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateRecon">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="disputeDialog" title="对账异议" width="520px" destroy-on-close @closed="reasonText = ''">
      <p class="dialog-hint">异议后进入「争议」状态，可与供应商协商后使用「重新打开」继续流程。</p>
      <el-input v-model="reasonText" type="textarea" :rows="4" placeholder="异议说明（必填）" maxlength="1000" show-word-limit />
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
.sub { font-size: 13px; color: var(--el-text-color-secondary); max-width: 560px; line-height: 1.45; }
.hint { margin-left: 4px; cursor: help; color: var(--el-color-info); font-size: 12px; }
.by { color: var(--el-text-color-secondary); font-size: 12px; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.dialog-hint { margin: 0 0 12px; font-size: 13px; color: var(--el-text-color-secondary); }
</style>
