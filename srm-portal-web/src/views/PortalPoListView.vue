<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalApi, type PoSummary } from '../api/portal'

const router = useRouter()
const rows = ref<PoSummary[]>([])

onMounted(async () => {
  const r = await portalApi.listPos()
  rows.value = r.data
})
</script>

<template>
  <div class="page">
    <h2 class="title">已发布采购订单</h2>
    <p class="hint">联调使用 <code>VITE_DEV_SUPPLIER_ID</code>（默认 1，对应种子供应商 S001）。</p>
    <el-table :data="rows" stripe style="margin-top: 16px" @row-dblclick="(row: PoSummary) => router.push(`/pos/${row.id}`)">
      <el-table-column prop="poNo" label="订单号" width="200" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="supplierName" label="供应商" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/pos/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
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
