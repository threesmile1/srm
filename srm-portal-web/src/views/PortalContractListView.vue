<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalContractApi, type PortalContractSummary, type PortalContractDetail } from '../api/contract'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const route = useRoute()
const router = useRouter()
const rows = ref<PortalContractSummary[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const current = ref<PortalContractDetail | null>(null)

const statusMap: Record<string, string> = {
  DRAFT: '草稿', ACTIVE: '生效', EXPIRED: '已过期', TERMINATED: '已终止',
}

async function load() {
  loading.value = true
  try {
    rows.value = (await portalContractApi.list()).data
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  try {
    current.value = (await portalContractApi.get(id)).data
    detailVisible.value = true
  } catch {
    current.value = null
  }
}

async function tryOpenFromQuery() {
  const cid = route.query.openId
  if (cid != null && cid !== '') {
    await openDetail(Number(cid))
    router.replace({ path: '/contracts', query: {} })
  }
}

onMounted(async () => {
  await load()
  await tryOpenFromQuery()
})

watch(
  () => route.query.openId,
  async (v) => {
    if (v != null && v !== '') {
      await tryOpenFromQuery()
    }
  },
)

async function showDetail(row: PortalContractSummary) {
  await openDetail(row.id)
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">我的合同</span>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <p class="hint">合同状态与条款以采购方系统为准，如有疑问请联系对接人。</p>
    <el-table v-loading="loading" :data="rows" stripe @row-dblclick="showDetail">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="contractNo" label="合同号" width="160" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ statusMap[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始" width="110" />
      <el-table-column prop="endDate" label="结束" width="110" />
      <el-table-column prop="totalAmount" label="金额" width="120" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="合同详情" width="880px" v-if="current">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="合同号">{{ current.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusMap[current.status] || current.status }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item label="采购组织">{{ current.procurementOrgCode }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ current.currency }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ current.startDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结束">{{ current.endDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ current.totalAmount ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="current.lines" border size="small" style="margin-top: 12px">
        <el-table-column prop="lineNo" label="#" width="50" />
        <el-table-column prop="materialCode" label="物料编码" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column prop="amount" label="金额" width="110" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
.title { font-size: 18px; font-weight: 600; }
.hint { font-size: 13px; color: var(--el-text-color-secondary); margin: 0 0 12px; }
</style>
