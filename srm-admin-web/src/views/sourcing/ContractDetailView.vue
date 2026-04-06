<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contractApi, type ContractDetail } from '../../api/contract'

const route = useRoute()
const router = useRouter()
const detail = ref<ContractDetail | null>(null)
const loading = ref(false)

const id = computed(() => Number(route.params.id))

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '生效',
  EXPIRED: '已过期',
  TERMINATED: '已终止',
}

const canActivate = computed(() => detail.value?.status === 'DRAFT')
const canTerminate = computed(() => detail.value?.status === 'ACTIVE')

async function load() {
  loading.value = true
  try {
    const r = await contractApi.get(id.value)
    detail.value = r.data
  } finally {
    loading.value = false
  }
}

async function activate() {
  try {
    await ElMessageBox.confirm('激活后合同进入生效状态，是否继续？', '激活合同', { type: 'warning' })
    await contractApi.activate(id.value)
    ElMessage.success('已激活')
    await load()
  } catch (e: unknown) {
    if (e === 'cancel') return
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
  }
}

async function terminate() {
  try {
    await ElMessageBox.confirm('终止后不可恢复为生效，是否继续？', '终止合同', { type: 'warning' })
    await contractApi.terminate(id.value)
    ElMessage.success('已终止')
    await load()
  } catch (e: unknown) {
    if (e === 'cancel') return
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
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
      <span class="title">合同详情</span>
      <div class="actions">
        <el-button v-if="canActivate" type="primary" @click="activate">激活</el-button>
        <el-button v-if="canTerminate" type="danger" plain @click="terminate">终止</el-button>
        <el-button @click="router.push('/sourcing/contracts')">返回列表</el-button>
      </div>
    </div>

    <template v-if="detail">
      <el-descriptions :column="2" border class="block">
        <el-descriptions-item label="合同号">{{ detail.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag>{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="供应商">
          {{ detail.supplierCode }} {{ detail.supplierName }}
        </el-descriptions-item>
        <el-descriptions-item label="采购组织">{{ detail.procurementOrgCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.contractType }}</el-descriptions-item>
        <el-descriptions-item label="币别">{{ detail.currency }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ detail.startDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结束">{{ detail.endDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="总额">{{ detail.totalAmount ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">合同行</h3>
      <el-table :data="detail.lines" border size="small">
        <el-table-column prop="lineNo" label="#" width="50" />
        <el-table-column label="物料" min-width="180">
          <template #default="{ row }">
            <template v-if="row.materialCode">{{ row.materialCode }} {{ row.materialName }}</template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="materialDesc" label="说明" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column prop="uom" label="单位" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="110" />
        <el-table-column prop="amount" label="金额" width="120" />
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
}
</style>
