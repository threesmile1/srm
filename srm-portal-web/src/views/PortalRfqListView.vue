<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalApi, type PortalRfqSummary } from '../api/portal'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const router = useRouter()
const rows = ref<PortalRfqSummary[]>([])

const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: '报价中',
  EVALUATING: '评估中',
  AWARDED: '已定标',
  CANCELLED: '已取消',
}

onMounted(async () => {
  const r = await portalApi.listRfq()
  rows.value = r.data
})

function statusLabel(s: string) {
  return STATUS_LABEL[s] ?? s
}
</script>

<template>
  <div class="page">
    <h2 class="title">询价单</h2>
    <p class="hint">展示已邀请本供应商参与的询价（含已发布、评估、已定标）。请在截止日前于详情页提交报价。</p>
    <el-table
      :data="rows"
      stripe
      style="margin-top: 16px"
      @row-dblclick="(row: PortalRfqSummary) => router.push(`/rfq/${row.id}`)"
    >
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
      <el-table-column prop="procurementOrgCode" label="采购组织" width="120" />
      <el-table-column prop="deadline" label="报价截止" width="120" />
      <el-table-column label="操作" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/rfq/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page {
  max-width: none;
  margin: 0;
  padding: 0;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}
</style>
