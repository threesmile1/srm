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

const statusMap: Record<string, string> = { DRAFT: '草稿', CONFIRMED: '已确认', DISPUTED: '有争议' }

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
  ElMessage.success('已确认')
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
      <el-table-column prop="reconNo" label="对账单号" width="180" />
      <el-table-column prop="supplierCode" label="供应商" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" width="160" />
      <el-table-column prop="periodFrom" label="开始日期" width="110" />
      <el-table-column prop="periodTo" label="结束日期" width="110" />
      <el-table-column prop="poAmount" label="PO金额" width="110" />
      <el-table-column prop="grAmount" label="收货金额" width="110" />
      <el-table-column prop="invoiceAmount" label="发票金额" width="110" />
      <el-table-column prop="diffAmount" label="差异" width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'DISPUTED' ? 'danger' : 'info'" size="small">
            {{ statusMap[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="confirmRecon(row.id)">确认</el-button>
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
