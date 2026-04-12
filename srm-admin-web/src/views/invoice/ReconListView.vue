<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { invoiceApi, type ReconSummary } from '../../api/invoice'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'recon-list')
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

async function confirmRecon(id: number) {
  await invoiceApi.confirmRecon(id)
  ElMessage.success('采购已确认对账')
  await loadData()
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
  await invoiceApi.procurementRejectRecon(id, t)
  ElMessage.success('已驳回至供应商')
  rejectDialog.value = false
  await loadData()
}

async function submitDispute() {
  const id = activeReconId.value
  const t = reasonText.value.trim()
  if (id == null || !t) {
    ElMessage.warning('请填写异议说明')
    return
  }
  await invoiceApi.procurementDisputeRecon(id, t)
  ElMessage.success('已记录异议')
  disputeDialog.value = false
  await loadData()
}

async function reopenRecon(id: number) {
  await invoiceApi.reopenRecon(id)
  ElMessage.success('已重新打开，待供应商确认')
  await loadData()
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">对账管理</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
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
      <el-table-column prop="poAmount" label="PO金额" width="110" />
      <el-table-column prop="grAmount" label="收货金额" width="110" />
      <el-table-column prop="invoiceAmount" label="已确认票额" width="120">
        <template #header>
          <span>已确认票额</span>
          <el-tooltip content="对账期间内、采购已确认的发票金额（未确认提交不计入）" placement="top">
            <span class="hint">?</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="diffAmount" label="收货−票" width="110" />
      <el-table-column prop="diffPoGrAmount" label="订单−收货" width="110" />
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
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
.hint { margin-left: 4px; cursor: help; color: var(--el-color-info); font-size: 12px; }
.by { color: var(--el-text-color-secondary); font-size: 12px; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.dialog-hint { margin: 0 0 12px; font-size: 13px; color: var(--el-text-color-secondary); }
</style>
