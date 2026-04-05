<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { purchaseApi, type PoSummary } from '../../api/purchase'
import { executionApi, downloadArrayBuffer } from '../../api/execution'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
const rows = ref<PoSummary[]>([])
const tableRef = ref()

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
  if (orgs.value.length && orgId.value == null) {
    orgId.value = orgs.value[0].id
  }
}

async function loadPos() {
  if (orgId.value == null) return
  const r = await purchaseApi.list(orgId.value)
  rows.value = r.data
}

watch(orgId, () => {
  loadPos()
})

onMounted(async () => {
  await loadOrgs()
  await loadPos()
})

function goDetail(id: number) {
  router.push(`/purchase/orders/${id}`)
}

async function exportSelected() {
  const sel: PoSummary[] = tableRef.value?.getSelectionRows?.() ?? []
  if (!sel.length) {
    ElMessage.warning('请选择要导出的订单')
    return
  }
  try {
    const r = await executionApi.exportPurchaseOrders(sel.map((x) => x.id))
    downloadArrayBuffer(r.data, 'srm-purchase-orders.xlsx')
    ElMessage.success('已下载')
    await loadPos()
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
      <span class="title">采购订单</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="$router.push('/purchase/orders/new')">新建</el-button>
      <el-button @click="exportSelected">导出选中（U9）</el-button>
    </div>
    <el-table ref="tableRef" :data="rows" stripe @row-dblclick="(row: PoSummary) => goDetail(row.id)">
      <el-table-column type="selection" width="42" />
      <el-table-column prop="poNo" label="订单号" width="200" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="supplierCode" label="供应商" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="exportStatus" label="导出" width="100" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
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
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
</style>
