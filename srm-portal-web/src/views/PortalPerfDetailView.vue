<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalPerfApi, type PortalEvalDetail } from '../api/perf'

const route = useRoute()
const router = useRouter()
const detail = ref<PortalEvalDetail | null>(null)
const loading = ref(false)

async function load() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    detail.value = (await portalPerfApi.getEvaluation(id)).data
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)

function back() {
  router.push('/perf')
}
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="toolbar">
      <el-button text @click="back">← 返回列表</el-button>
    </div>
    <template v-if="detail">
      <h2 class="title">考核详情 · {{ detail.period }}</h2>
      <el-descriptions :column="2" border size="small" class="meta">
        <el-descriptions-item label="模板">{{ detail.templateName }}</el-descriptions-item>
        <el-descriptions-item label="等级">
          <el-tag type="success" v-if="detail.grade === 'A'">{{ detail.grade }}</el-tag>
          <el-tag type="primary" v-else-if="detail.grade === 'B'">{{ detail.grade }}</el-tag>
          <el-tag type="warning" v-else-if="detail.grade === 'C'">{{ detail.grade }}</el-tag>
          <el-tag type="danger" v-else>{{ detail.grade || '—' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总分">{{ detail.totalScore }}</el-descriptions-item>
        <el-descriptions-item label="评价人">{{ detail.evaluatorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.scores" border size="small" class="score-table">
        <el-table-column prop="dimensionName" label="维度" min-width="140" />
        <el-table-column prop="weight" label="权重%" width="90" />
        <el-table-column prop="score" label="得分" width="90" />
        <el-table-column prop="comment" label="说明" min-width="200" show-overflow-tooltip />
      </el-table>
    </template>
    <el-empty v-else-if="!loading" description="记录不存在或尚未发布" />
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { margin-bottom: 12px; }
.title { font-size: 18px; font-weight: 600; margin: 0 0 16px; }
.meta { margin-bottom: 16px; }
.score-table { margin-top: 8px; }
</style>
