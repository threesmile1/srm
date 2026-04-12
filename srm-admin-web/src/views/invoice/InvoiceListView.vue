<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { invoiceApi, type InvoiceSummary } from '../../api/invoice'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'invoice-list')
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

function goDetail(id: number) { router.push(`/invoice/${id}`) }

async function confirm(id: number) {
  await invoiceApi.confirm(id)
  ElMessage.success('已确认')
  await loadData()
}

async function reject(id: number) {
  await invoiceApi.reject(id)
  ElMessage.success('已退回')
  await loadData()
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">发票管理</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
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
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" link type="success" @click="confirm(row.id)">确认</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" link type="danger" @click="reject(row.id)">退回</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
</style>
