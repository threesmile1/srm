<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { rfqApi, type RfqSummary } from '../../api/rfq'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'rfq-list')
const rows = ref<RfqSummary[]>([])

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  EVALUATING: '评估中',
  AWARDED: '已定标',
  CANCELLED: '已取消',
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadRows() {
  if (orgId.value == null) return
  const r = await rfqApi.list(orgId.value)
  rows.value = r.data
}

watch(orgId, () => loadRows())

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
      <span class="title">询价单（RFQ）</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 240px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="router.push('/sourcing/rfq/new')">新建询价</el-button>
    </div>
    <el-table :data="rows" stripe @row-dblclick="(row: RfqSummary) => router.push(`/sourcing/rfq/${row.id}`)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="rfqNo" label="询价单号" width="160" />
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishDate" label="发布日" width="120" />
      <el-table-column prop="deadline" label="截止日" width="120" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/sourcing/rfq/${row.id}`)">详情</el-button>
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
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin-right: auto;
}
</style>
