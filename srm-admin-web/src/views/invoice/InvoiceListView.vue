<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { invoiceApi, type InvoiceSummary } from '../../api/invoice'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import { useAuthStore } from '../../stores/auth'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const auth = useAuthStore()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'invoice-list', () => auth.user?.defaultProcurementOrgId ?? null)
const rows = ref<InvoiceSummary[]>([])

const statusMap: Record<string, string> = {
  SUBMITTED: '已提交', CONFIRMED: '已确认', REJECTED: '已退回', CANCELLED: '已取消',
}

const kindMap: Record<string, string> = {
  ORDINARY_VAT: '普票',
  SPECIAL_VAT: '专票',
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadData() {
  if (orgId.value == null) return
  rows.value = (await invoiceApi.list({ procurementOrgId: orgId.value })).data
}

watch(orgId, () => loadData())
onMounted(async () => { await loadOrgs(); await loadData() })

function goDetail(id: number) {
  router.push(`/invoice/${id}`)
}

function apiErrorMessage(e: unknown): string {
  if (e && typeof e === 'object' && 'response' in e) {
    const d = (e as { response?: { data?: { error?: string } } }).response?.data
    if (d?.error) return d.error
  }
  return ''
}

async function confirm(id: number) {
  const loading = ElLoading.service({ lock: true, text: '确认中…' })
  try {
    await invoiceApi.confirm(id)
    ElMessage.success('已确认')
    await loadData()
  } catch (e: unknown) {
    ElMessage.error(apiErrorMessage(e) || '确认失败，请稍后重试或查看网络')
  } finally {
    loading.close()
  }
}

async function reject(id: number) {
  const loading = ElLoading.service({ lock: true, text: '处理中…' })
  try {
    await invoiceApi.reject(id)
    ElMessage.success('已退回')
    await loadData()
  } catch (e: unknown) {
    ElMessage.error(apiErrorMessage(e) || '退回失败，请稍后重试或查看网络')
  } finally {
    loading.close()
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">发票管理</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <span v-if="orgId != null" class="hint">列表按所选采购组织过滤，需与供应商开票时选择的组织一致。</span>
    </div>
    <el-table :data="rows" stripe @row-dblclick="(row: InvoiceSummary) => goDetail(row.id)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="invoiceNo" label="发票号" width="180" />
      <el-table-column label="票种" width="72">
        <template #default="{ row }">{{ kindMap[row.invoiceKind] || row.invoiceKind }}</template>
      </el-table-column>
      <el-table-column prop="supplierCode" label="供应商" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" width="160" />
      <el-table-column prop="invoiceDate" label="开票日期" width="110" />
      <el-table-column prop="totalAmount" label="金额" width="120" />
      <el-table-column prop="taxAmount" label="税额" width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">
            {{ statusMap[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="goDetail(row.id)">详情</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" link type="success" @click.stop="confirm(row.id)">确认</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" link type="danger" @click.stop="reject(row.id)">退回</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
.hint { font-size: 13px; color: var(--el-text-color-secondary); max-width: 420px; line-height: 1.4; }
</style>
