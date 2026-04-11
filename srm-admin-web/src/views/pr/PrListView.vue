<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { prApi, type PrSummary } from '../../api/pr'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'pr-list')
const rows = ref<PrSummary[]>([])

const statusMap: Record<string, string> = {
  DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准',
  PARTIALLY_CONVERTED: '部分转单', FULLY_CONVERTED: '已转单',
  REJECTED: '已驳回', CANCELLED: '已取消',
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadData() {
  if (orgId.value == null) return
  rows.value = (await prApi.list(orgId.value)).data
}

/** orgId 由 usePersistedProcurementOrg 在 orgs 加载后恢复；须等 orgId 就绪再拉列表，否则会按 null 提前 return */
watch(orgId, () => loadData(), { immediate: true })

onMounted(async () => {
  await loadOrgs()
})

function goDetail(id: number) { router.push(`/pr/${id}`) }
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">请购单</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="$router.push('/pr/new')">新建请购</el-button>
    </div>
    <el-table :data="rows" stripe @row-dblclick="(row: PrSummary) => goDetail(row.id)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="prNo" label="请购单号" width="200" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="requesterName" label="申请人" width="120" />
      <el-table-column prop="department" label="部门" width="140" />
      <el-table-column prop="procurementOrgCode" label="采购组织" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 600; }
</style>
