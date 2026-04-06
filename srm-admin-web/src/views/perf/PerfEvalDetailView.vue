<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { perfApi, type EvalDetail } from '../../api/perf'

const route = useRoute()
const detail = ref<EvalDetail | null>(null)

const statusMap: Record<string, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PUBLISHED: '已发布' }
const gradeColor: Record<string, string> = { A: '#67c23a', B: '#409eff', C: '#e6a23c', D: '#f56c6c' }

async function load() {
  detail.value = (await perfApi.getEvaluation(Number(route.params.id))).data
}

onMounted(load)

async function submit() {
  await perfApi.submitEvaluation(detail.value!.id)
  ElMessage.success('已提交')
  await load()
}

async function publish() {
  await perfApi.publishEvaluation(detail.value!.id)
  ElMessage.success('已发布')
  await load()
}
</script>

<template>
  <div class="page" v-if="detail">
    <div class="toolbar">
      <span class="title">绩效考核详情</span>
      <el-tag v-if="detail.grade" :color="gradeColor[detail.grade]" style="color:#fff" size="large">{{ detail.grade }}级</el-tag>
      <div style="flex: 1" />
      <el-button @click="$router.back()">返回</el-button>
      <el-button v-if="detail.status === 'DRAFT'" type="primary" @click="submit">提交</el-button>
      <el-button v-if="detail.status === 'SUBMITTED'" type="success" @click="publish">发布</el-button>
    </div>

    <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
      <el-descriptions-item label="供应商">{{ detail.supplierCode }} {{ detail.supplierName }}</el-descriptions-item>
      <el-descriptions-item label="模板">{{ detail.templateName }}</el-descriptions-item>
      <el-descriptions-item label="期间">{{ detail.period }}</el-descriptions-item>
      <el-descriptions-item label="综合得分">{{ detail.totalScore }}</el-descriptions-item>
      <el-descriptions-item label="等级">{{ detail.grade }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusMap[detail.status] || detail.status }}</el-descriptions-item>
      <el-descriptions-item label="评分人">{{ detail.evaluatorName }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
    </el-descriptions>

    <el-table :data="detail.scores" border size="small">
      <el-table-column prop="dimensionName" label="考核维度" width="140" />
      <el-table-column label="权重(%)" width="100">
        <template #default="{ row }">{{ row.weight }}</template>
      </el-table-column>
      <el-table-column label="评分" width="100">
        <template #default="{ row }">{{ row.score }}</template>
      </el-table-column>
      <el-table-column prop="comment" label="评语" />
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
</style>
