<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { masterApi, type Supplier } from '../../api/master'
import { perfApi, type PerfTemplate, type PerfTemplateDetail, type PerfDimension } from '../../api/perf'

const router = useRouter()
const suppliers = ref<Supplier[]>([])
const templates = ref<PerfTemplate[]>([])
const templateDetail = ref<PerfTemplateDetail | null>(null)

const form = ref({
  supplierId: null as number | null,
  templateId: null as number | null,
  period: '',
  evaluatorName: '',
  remark: '',
})

type ScoreRow = { dimensionId: number; name: string; weight: string; score: number; comment: string }
const scores = ref<ScoreRow[]>([])

async function loadTemplate() {
  if (!form.value.templateId) return
  templateDetail.value = (await perfApi.getTemplate(form.value.templateId)).data
  scores.value = templateDetail.value.dimensions.map((d: PerfDimension) => ({
    dimensionId: d.id, name: d.name, weight: d.weight, score: 0, comment: '',
  }))
}

watch(() => form.value.templateId, loadTemplate)

onMounted(async () => {
  suppliers.value = (await masterApi.listSuppliers()).data
  templates.value = (await perfApi.listTemplates()).data
  if (templates.value.length) {
    form.value.templateId = templates.value[0].id
  }
})

async function save() {
  if (!form.value.supplierId) { ElMessage.warning('请选择供应商'); return }
  if (!form.value.templateId) { ElMessage.warning('请选择考核模板'); return }
  if (!form.value.period) { ElMessage.warning('请填写考核期间'); return }
  try {
    const r = await perfApi.createEvaluation({
      supplierId: form.value.supplierId,
      templateId: form.value.templateId,
      period: form.value.period,
      evaluatorName: form.value.evaluatorName || undefined,
      remark: form.value.remark || undefined,
      scores: scores.value.map((s) => ({ dimensionId: s.dimensionId, score: s.score, comment: s.comment || undefined })),
    })
    ElMessage.success('考核创建成功，综合得分: ' + r.data.totalScore)
    router.push(`/perf/evaluations/${r.data.id}`)
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e
      ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '创建失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">新建供应商考核</span>
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </div>
    <el-form label-width="100px" style="max-width: 700px">
      <el-form-item label="供应商">
        <el-select v-model="form.supplierId" filterable placeholder="选择供应商" style="width: 100%">
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="考核模板">
        <el-select v-model="form.templateId" style="width: 100%">
          <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="考核期间">
        <el-input v-model="form.period" placeholder="如: 2026-Q1" />
      </el-form-item>
      <el-form-item label="评分人">
        <el-input v-model="form.evaluatorName" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>

    <h4 style="margin: 16px 0 8px">考核维度评分</h4>
    <el-table :data="scores" border size="small">
      <el-table-column prop="name" label="维度" width="140" />
      <el-table-column label="权重(%)" width="100">
        <template #default="{ row }">{{ row.weight }}</template>
      </el-table-column>
      <el-table-column label="评分(0-100)" width="160">
        <template #default="{ row }">
          <el-input-number v-model="row.score" :min="0" :max="100" :precision="1" size="small" controls-position="right" />
        </template>
      </el-table-column>
      <el-table-column label="评语">
        <template #default="{ row }">
          <el-input v-model="row.comment" size="small" />
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
