<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { executionApi, type PurchaseExecutionRow } from '../../api/execution'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'report-execution')
const rows = ref<PurchaseExecutionRow[]>([])

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadReport() {
  if (orgId.value == null) return
  const r = await executionApi.purchaseExecutionReport(orgId.value)
  rows.value = r.data
}

watch(orgId, () => {
  loadReport()
})

onMounted(async () => {
  await loadOrgs()
  await loadReport()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">采购执行（在途）</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
    </div>
    <p class="hint">已发布/已关闭订单行：订购、已收、未清数量。</p>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="poNo" label="订单号" width="160" />
      <el-table-column prop="poStatus" label="订单状态" width="100" />
      <el-table-column prop="businessDate" label="业务日期" width="110" />
      <el-table-column prop="officialOrderNo" label="正式订单号" width="160" show-overflow-tooltip />
      <el-table-column prop="store2" label="二级门店" width="120" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="收货人" width="110" show-overflow-tooltip />
      <el-table-column prop="terminalPhone" label="终端电话" width="120" show-overflow-tooltip />
      <el-table-column prop="installAddress" label="安装地址" width="220" show-overflow-tooltip />
      <el-table-column prop="lineNo" label="行" width="50" />
      <el-table-column prop="materialCode" label="物料" width="120" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column prop="orderedQty" label="订购" width="90" />
      <el-table-column prop="receivedQty" label="已收" width="90" />
      <el-table-column prop="openQty" label="未清" width="90" />
    </el-table>
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
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
}
</style>
