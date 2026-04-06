<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalPerfApi, type PortalEvalSummary } from '../api/perf'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const router = useRouter()
const rows = ref<PortalEvalSummary[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = (await portalPerfApi.listEvaluations()).data
  } finally {
    loading.value = false
  }
}

onMounted(load)

function goDetail(row: PortalEvalSummary) {
  router.push(`/perf/${row.id}`)
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">绩效考核</span>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <p class="hint">以下为采购方已发布的考核记录，仅供查阅。</p>
    <el-table v-loading="loading" :data="rows" stripe @row-dblclick="goDetail">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="period" label="考核周期" width="120" />
      <el-table-column prop="totalScore" label="得分" width="90" />
      <el-table-column prop="grade" label="等级" width="80" />
      <el-table-column prop="evaluatorName" label="评价人" width="120" />
      <el-table-column label="操作" width="88">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
.title { font-size: 18px; font-weight: 600; }
.hint { font-size: 13px; color: var(--el-text-color-secondary); margin: 0 0 12px; }
.muted { color: var(--el-text-color-secondary); }
</style>
