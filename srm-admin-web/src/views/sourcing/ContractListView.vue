<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { contractApi, type ContractSummary } from '../../api/contract'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'contract-list')
const tab = ref<'all' | 'expiring'>('all')
const rows = ref<ContractSummary[]>([])

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '生效',
  EXPIRED: '已过期',
  TERMINATED: '已终止',
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadRows() {
  if (orgId.value == null) {
    rows.value = []
    return
  }
  if (tab.value === 'all') {
    const r = await contractApi.list(orgId.value)
    rows.value = r.data
  } else {
    const r = await contractApi.listExpiring(orgId.value, 30)
    rows.value = r.data
  }
}

watch([orgId, tab], () => loadRows())

onMounted(async () => {
  await loadOrgs()
  await loadRows()
})

function statusLabel(s: string) {
  return STATUS_LABEL[s] ?? s
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">合同台账</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 240px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="router.push('/sourcing/contracts/new')">新建合同</el-button>
    </div>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane label="全部合同" name="all" />
      <el-tab-pane label="即将到期（30天内）" name="expiring" />
    </el-tabs>

    <el-table :data="rows" stripe @row-dblclick="(row: ContractSummary) => router.push(`/sourcing/contracts/${row.id}`)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="contractNo" label="合同号" width="150" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="供应商" min-width="160">
        <template #default="{ row }">{{ row.supplierCode }} {{ row.supplierName }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contractType" label="类型" width="110" />
      <el-table-column prop="startDate" label="开始" width="110" />
      <el-table-column prop="endDate" label="结束" width="110" />
      <el-table-column prop="totalAmount" label="总额" width="120" />
      <el-table-column prop="currency" label="币别" width="72" />
      <el-table-column label="操作" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/sourcing/contracts/${row.id}`)">详情</el-button>
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
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin-right: auto;
}
.tabs {
  margin-bottom: 12px;
}
</style>
