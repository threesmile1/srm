<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { prApi, type PrDetail, type PrLine } from '../../api/pr'
import { approvalApi, type ApprovalInstance } from '../../api/approval'

const route = useRoute()
const router = useRouter()
const pr = ref<PrDetail | null>(null)
const selectedLineIds = ref<number[]>([])
const approvalInst = ref<ApprovalInstance | null | undefined>(undefined)

const workflowPending = computed(
  () => pr.value?.status === 'PENDING_APPROVAL' && approvalInst.value?.status === 'PENDING',
)

const statusMap: Record<string, string> = {
  DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准',
  PARTIALLY_CONVERTED: '部分转单', FULLY_CONVERTED: '已转单',
  REJECTED: '已驳回', CANCELLED: '已取消',
}

const canSubmit = computed(() => pr.value?.status === 'DRAFT')
const canApprove = computed(() => pr.value?.status === 'PENDING_APPROVAL')
const canConvert = computed(() =>
  pr.value?.status === 'APPROVED' || pr.value?.status === 'PARTIALLY_CONVERTED')
const canCancel = computed(() =>
  pr.value?.status === 'DRAFT' || pr.value?.status === 'APPROVED')

async function load() {
  const id = Number(route.params.id)
  pr.value = (await prApi.get(id)).data
  if (pr.value?.status === 'PENDING_APPROVAL') {
    try {
      const ar = await approvalApi.getByDoc('PR', id)
      approvalInst.value = ar.data
    } catch {
      approvalInst.value = null
    }
  } else {
    approvalInst.value = undefined
  }
}

onMounted(load)

async function submit() {
  await prApi.submit(pr.value!.id)
  ElMessage.success('已提交审批')
  await load()
}

async function approve() {
  await prApi.approve(pr.value!.id)
  ElMessage.success('已审批通过')
  await load()
}

async function reject() {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回')
  await prApi.reject(pr.value!.id, value)
  ElMessage.success('已驳回')
  await load()
}

async function cancel() {
  await ElMessageBox.confirm('确认取消？')
  await prApi.cancel(pr.value!.id)
  ElMessage.success('已取消')
  await load()
}

function onSelectionChange(selection: PrLine[]) {
  selectedLineIds.value = selection.map((l) => l.id)
}

async function convertToPo() {
  const convertable = selectedLineIds.value.filter((id) => {
    const line = pr.value!.lines.find((l) => l.id === id)
    return line && !line.convertedPoId
  })
  if (!convertable.length) {
    ElMessage.warning('请选择未转单的行')
    return
  }
  try {
    const r = await prApi.convertToPo(pr.value!.id, convertable)
    ElMessage.success(`已创建 ${r.data.length} 个采购订单`)
    await load()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e
      ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '转单失败')
  }
}
</script>

<template>
  <div class="page" v-if="pr">
    <div class="toolbar">
      <span class="title">{{ pr.prNo }}</span>
      <el-tag :type="pr.status === 'APPROVED' ? 'success' : pr.status === 'REJECTED' ? 'danger' : 'info'">
        {{ statusMap[pr.status] || pr.status }}
      </el-tag>
      <div style="flex: 1" />
      <el-button @click="$router.back()">返回</el-button>
      <el-button v-if="canSubmit" type="primary" @click="submit">提交审批</el-button>
      <el-button v-if="canApprove && !workflowPending" type="success" @click="approve">审批通过</el-button>
      <el-button v-if="canApprove && !workflowPending" type="danger" @click="reject">驳回</el-button>
      <el-button v-if="workflowPending" type="primary" link @click="router.push('/approval/list')">
        前往审批中心
      </el-button>
      <el-button v-if="canConvert" type="warning" @click="convertToPo">选中行转PO</el-button>
      <el-button v-if="canCancel" @click="cancel">取消</el-button>
    </div>
    <el-alert
      v-if="workflowPending"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="该请购单已关联审批流程，请在「审批中心」审批或驳回。"
    />
    <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
      <el-descriptions-item label="采购组织">{{ pr.procurementOrgCode }}</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ pr.requesterName }}</el-descriptions-item>
      <el-descriptions-item label="部门">{{ pr.department }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="3">{{ pr.remark }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="pr.lines" border size="small" @selection-change="onSelectionChange">
      <el-table-column v-if="canConvert" type="selection" width="42" :selectable="(row: PrLine) => !row.convertedPoId" />
      <el-table-column prop="lineNo" label="#" width="50" />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" />
      <el-table-column prop="qty" label="数量" width="90" />
      <el-table-column prop="uom" label="单位" width="70" />
      <el-table-column prop="unitPrice" label="单价" width="90" />
      <el-table-column prop="requestedDate" label="交期" width="110" />
      <el-table-column prop="supplierCode" label="供应商" width="110" />
      <el-table-column prop="warehouseCode" label="仓库" width="100" />
      <el-table-column label="已转PO" width="160">
        <template #default="{ row }">
          <el-button v-if="row.convertedPoNo" link type="primary"
                     @click="router.push(`/purchase/orders/${row.convertedPoId}`)">
            {{ row.convertedPoNo }}
          </el-button>
          <span v-else style="color: #999">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 600; }
</style>
