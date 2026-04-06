<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { perfApi, type EvalSummary } from '../../api/perf'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const rows = ref<EvalSummary[]>([])

const statusMap: Record<string, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PUBLISHED: '已发布' }
const gradeColor: Record<string, string> = { A: '#67c23a', B: '#409eff', C: '#e6a23c', D: '#f56c6c' }

async function load() {
  rows.value = (await perfApi.listEvaluations()).data
}

onMounted(load)

function goDetail(id: number) { router.push(`/perf/evaluations/${id}`) }
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">供应商绩效考核</span>
      <el-button type="primary" @click="$router.push('/perf/evaluations/new')">新建考核</el-button>
    </div>
    <el-table :data="rows" stripe @row-dblclick="(row: EvalSummary) => goDetail(row.id)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="supplierCode" label="供应商编码" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" width="180" />
      <el-table-column prop="period" label="考核期间" width="120" />
      <el-table-column label="综合得分" width="100">
        <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="等级" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.grade" :color="gradeColor[row.grade]" style="color:#fff" size="small">{{ row.grade }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="evaluatorName" label="评分人" width="120" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
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
