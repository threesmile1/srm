<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { approvalApi, type ApprovalInstance } from '../../api/approval'
import { useAuthStore } from '../../stores/auth'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const instances = ref<ApprovalInstance[]>([])
const filterStatus = ref('')
const detailVisible = ref(false)
const current = ref<ApprovalInstance | null>(null)
const quickLoadingId = ref<number | null>(null)

const statusMap: Record<string, string> = {
  PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消',
}

function docTypeLabel(t: string) {
  if (t === 'PR') return '请购单'
  if (t === 'PO') return '采购订单'
  if (t === 'GR') return '收货单'
  if (t === 'ASN') return '发货通知'
  return t
}

function currentApprover() {
  const id = auth.userId
  const name = (auth.displayName || auth.username || '').trim()
  if (id == null) {
    ElMessage.error('未获取到登录用户，请重新登录后再试')
    return null
  }
  if (!name) {
    ElMessage.error('未获取到用户显示名，请重新登录后再试')
    return null
  }
  return { approverId: id, approverName: name }
}

async function loadInstances() {
  instances.value = (await approvalApi.listInstances(filterStatus.value || undefined)).data
}

async function tryOpenInstanceFromQuery() {
  const raw = route.query.openInstanceId
  if (raw == null || raw === '') return
  const id = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!Number.isFinite(id)) return
  try {
    current.value = (await approvalApi.getInstance(id)).data
    detailVisible.value = true
  } catch {
    ElMessage.warning('未找到审批实例或无权查看')
  }
  const q = { ...route.query } as Record<string, string | string[]>
  delete q.openInstanceId
  await router.replace({ path: route.path, query: q })
}

onMounted(async () => {
  await loadInstances()
  await tryOpenInstanceFromQuery()
})

async function showDetail(row: ApprovalInstance) {
  current.value = (await approvalApi.getInstance(row.id)).data
  detailVisible.value = true
}

/** 成功时返回审批人展示名；未登录等返回 null（已 toast） */
async function submitProcess(
  instanceId: number,
  action: 'APPROVED' | 'REJECTED',
  comment: string,
): Promise<string | null> {
  const ap = currentApprover()
  if (!ap) return null
  await approvalApi.processAction(instanceId, {
    action,
    approverId: ap.approverId,
    approverName: ap.approverName,
    comment: comment || undefined,
  })
  return ap.approverName
}

async function doAction(action: 'APPROVED' | 'REJECTED') {
  if (!current.value) return
  let comment = ''
  if (action === 'REJECTED') {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回')
    comment = value ?? ''
  }
  try {
    const name = await submitProcess(current.value.id, action, comment)
    if (!name) return
    ElMessage.success(action === 'APPROVED' ? `审批通过（${name}）` : `已驳回（${name}）`)
    detailVisible.value = false
    await loadInstances()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
  }
}

async function quickApprove(row: ApprovalInstance) {
  if (row.status !== 'PENDING') return
  const ap = currentApprover()
  if (!ap) return
  try {
    await ElMessageBox.confirm(
      `确认以「${ap.approverName}」通过当前审批级？\n${docTypeLabel(row.docType)} ${row.docNo}（第 ${row.currentLevel} 级）`,
      '一键通过',
      { type: 'warning', confirmButtonText: '通过', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  quickLoadingId.value = row.id
  try {
    const name = await submitProcess(row.id, 'APPROVED', '')
    if (name) ElMessage.success(`已通过，审批人：${name}`)
    await loadInstances()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
  } finally {
    quickLoadingId.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">审批工作台</span>
      <el-select v-model="filterStatus" clearable placeholder="全部状态" style="width: 150px" @change="loadInstances">
        <el-option label="待审批" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
      </el-select>
    </div>
    <el-table :data="instances" stripe @row-dblclick="showDetail">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column label="单据类型" width="100">
        <template #default="{ row }">
          {{ docTypeLabel(row.docType) }}
        </template>
      </el-table-column>
      <el-table-column prop="docNo" label="单据编号" width="200" />
      <el-table-column prop="totalAmount" label="金额" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PENDING' ? 'warning' : row.status === 'APPROVED' ? 'success' : 'danger'" size="small">
            {{ statusMap[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentLevel" label="当前级别" width="90" />
      <el-table-column prop="createdAt" label="发起时间" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看</el-button>
          <el-button
            v-if="row.status === 'PENDING'"
            link
            type="success"
            :loading="quickLoadingId === row.id"
            @click="quickApprove(row)"
          >
            一键通过
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="审批详情" width="700px" v-if="current">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="单据类型">{{ docTypeLabel(current.docType) }}</el-descriptions-item>
        <el-descriptions-item label="单据编号">{{ current.docNo }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ current.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusMap[current.status] || current.status }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin: 16px 0 8px">审批步骤</h4>
      <el-table :data="current.steps" border size="small">
        <el-table-column prop="stepLevel" label="级别" width="60" />
        <el-table-column prop="approverRole" label="审批角色" width="120" />
        <el-table-column prop="approverName" label="审批人" width="100" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.action" :type="row.action === 'APPROVED' ? 'success' : 'danger'" size="small">
              {{ row.action === 'APPROVED' ? '通过' : '驳回' }}
            </el-tag>
            <span v-else style="color: #999">待处理</span>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="意见" />
        <el-table-column prop="actedAt" label="时间" width="170" />
      </el-table>
      <template #footer v-if="current.status === 'PENDING'">
        <span v-if="auth.displayName || auth.username" class="footer-actor">
          当前审批人：{{ auth.displayName || auth.username }}
        </span>
        <el-button type="success" @click="doAction('APPROVED')">审批通过</el-button>
        <el-button type="danger" @click="doAction('REJECTED')">驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
.footer-actor { margin-right: 12px; font-size: 13px; color: #606266; vertical-align: middle; }
</style>
