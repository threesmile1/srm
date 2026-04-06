<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rfqApi, type QuotationSummary, type RfqDetail } from '../../api/rfq'

const route = useRoute()
const router = useRouter()
const detail = ref<RfqDetail | null>(null)
const quotations = ref<QuotationSummary[]>([])
const loading = ref(false)

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  EVALUATING: '评估中',
  AWARDED: '已定标',
  CANCELLED: '已取消',
}

const id = computed(() => Number(route.params.id))

const canPublish = computed(
  () => detail.value?.status === 'DRAFT' && (detail.value?.invitations?.length ?? 0) > 0,
)
const canAward = computed(() => detail.value?.status === 'PUBLISHED' || detail.value?.status === 'EVALUATING')

async function load() {
  loading.value = true
  try {
    const r = await rfqApi.get(id.value)
    detail.value = r.data
    const q = await rfqApi.listQuotations(id.value)
    quotations.value = q.data
  } finally {
    loading.value = false
  }
}

async function publish() {
  try {
    await ElMessageBox.confirm('发布后受邀供应商可在门户提交报价，是否继续？', '发布询价', { type: 'warning' })
    await rfqApi.publish(id.value)
    ElMessage.success('已发布')
    await load()
  } catch (e: unknown) {
    if (e === 'cancel') return
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '发布失败')
  }
}

async function awardRow(q: QuotationSummary) {
  try {
    await ElMessageBox.confirm(
      `确定将「${q.supplierName}」定为中标供应商？`,
      '定标',
      { type: 'warning' },
    )
    await rfqApi.award(id.value, q.supplierId)
    ElMessage.success('已定标')
    await load()
  } catch (e: unknown) {
    if (e === 'cancel') return
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '定标失败')
  }
}

function statusLabel(s: string) {
  return STATUS_LABEL[s] ?? s
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="toolbar">
      <span class="title">询价详情</span>
      <div class="actions">
        <el-button v-if="canPublish" type="primary" @click="publish">发布</el-button>
        <el-button @click="router.push('/sourcing/rfq')">返回列表</el-button>
      </div>
    </div>

    <template v-if="detail">
      <el-descriptions :column="2" border class="block">
        <el-descriptions-item label="单号">{{ detail.rfqNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag>{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="采购组织">{{ detail.procurementOrgCode }}</el-descriptions-item>
        <el-descriptions-item label="截止日">{{ detail.deadline || '—' }}</el-descriptions-item>
        <el-descriptions-item label="发布日">{{ detail.publishDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">询价行</h3>
      <el-table :data="detail.lines" border size="small">
        <el-table-column prop="lineNo" label="#" width="50" />
        <el-table-column label="物料" min-width="200">
          <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column prop="uom" label="单位" width="80" />
        <el-table-column prop="specification" label="规格" show-overflow-tooltip />
      </el-table>

      <h3 class="section-title">受邀供应商</h3>
      <el-table :data="detail.invitations" border size="small">
        <el-table-column prop="supplierCode" label="编码" width="120" />
        <el-table-column prop="supplierName" label="名称" />
        <el-table-column label="已报价" width="100">
          <template #default="{ row }">
            <el-tag :type="row.responded ? 'success' : 'info'" size="small">
              {{ row.responded ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <h3 class="section-title">报价一览</h3>
      <el-table :data="quotations" border size="small">
        <el-table-column prop="supplierCode" label="供应商编码" width="120" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="totalAmount" label="总金额" width="120" />
        <el-table-column prop="currency" label="币别" width="72" />
        <el-table-column prop="deliveryDays" label="交货期(天)" width="110" />
        <el-table-column prop="submittedAt" label="提交时间" width="180" />
        <el-table-column v-if="canAward" label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.submittedAt"
              link
              type="primary"
              @click="awardRow(row)"
            >
              定标
            </el-button>
            <span v-else class="muted">待提交</span>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 8px;
}
.block {
  margin-bottom: 20px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 10px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
