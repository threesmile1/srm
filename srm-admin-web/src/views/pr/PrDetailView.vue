<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { prApi, type PrDetail, type PrLine } from '../../api/pr'
import { approvalApi, type ApprovalInstance } from '../../api/approval'
import { masterApi, type Supplier } from '../../api/master'

const route = useRoute()
const router = useRouter()
const pr = ref<PrDetail | null>(null)
const selectedLineIds = ref<number[]>([])
const approvalInst = ref<ApprovalInstance | null | undefined>(undefined)
const suppliers = ref<Supplier[]>([])
const convertDialogVisible = ref(false)

/** 转 PO 弹窗：采购填写供应商、单价、约定交期 */
type ConvertDraftRow = {
  lineId: number
  lineNo: number
  materialCode: string
  materialName: string
  refUnitPrice: string | null
  prRequestedDate: string | null
  supplierId: number | null
  unitPrice: number | undefined
  promisedDate: string | null
}
const convertDraft = ref<ConvertDraftRow[]>([])

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
  if (!suppliers.value.length) {
    const s = await masterApi.listSuppliers()
    suppliers.value = s.data
  }
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

function openConvertDialog() {
  const convertable = selectedLineIds.value.filter((id) => {
    const line = pr.value!.lines.find((l) => l.id === id)
    return line && !line.convertedPoId
  })
  if (!convertable.length) {
    ElMessage.warning('请选择未转单的行')
    return
  }
  convertDraft.value = convertable.map((id) => {
    const line = pr.value!.lines.find((l) => l.id === id)!
    const refPx = line.unitPrice != null && line.unitPrice !== '' ? Number(line.unitPrice) : undefined
    return {
      lineId: line.id,
      lineNo: line.lineNo,
      materialCode: line.materialCode,
      materialName: line.materialName,
      refUnitPrice: line.unitPrice,
      prRequestedDate: line.requestedDate,
      supplierId: line.supplierId ?? null,
      unitPrice: refPx !== undefined && !Number.isNaN(refPx) ? refPx : undefined,
      promisedDate: line.requestedDate ?? null,
    }
  })
  convertDialogVisible.value = true
}

function onConvertDialogClosed() {
  convertDraft.value = []
}

async function confirmConvertToPo() {
  for (const row of convertDraft.value) {
    if (row.supplierId == null) {
      ElMessage.warning(`第 ${row.lineNo} 行请选择供应商`)
      return
    }
    if (row.unitPrice == null || row.unitPrice <= 0) {
      ElMessage.warning(`第 ${row.lineNo} 行请填写有效采购单价`)
      return
    }
  }
  try {
    const r = await prApi.convertToPo(
      pr.value!.id,
      convertDraft.value.map((row) => ({
        lineId: row.lineId,
        supplierId: row.supplierId!,
        unitPrice: row.unitPrice!,
        requestedDate: row.promisedDate || undefined,
      })),
    )
    ElMessage.success(`已创建 ${r.data.length} 个采购订单`)
    convertDialogVisible.value = false
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
      <el-button v-if="canConvert" type="warning" @click="openConvertDialog">转采购订单…</el-button>
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
      <el-table-column prop="unitPrice" label="参考单价" width="100" />
      <el-table-column prop="requestedDate" label="需求交期" width="110" />
      <el-table-column prop="supplierCode" label="建议供应商" width="120" />
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

    <el-dialog
      v-model="convertDialogVisible"
      title="转采购订单（请确认供应商、单价与约定交期）"
      width="920px"
      destroy-on-close
      @closed="onConvertDialogClosed"
    >
      <el-alert
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
        title="请购侧数据为参考；正式商务条件以本页填写的供应商、采购单价及约定交期为准。未填约定交期时将采用请购行上的需求交期。"
      />
      <el-table :data="convertDraft" border size="small">
        <el-table-column prop="lineNo" label="#" width="44" />
        <el-table-column prop="materialCode" label="物料编码" width="110" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="参考单价" width="100">
          <template #default="{ row }">{{ row.refUnitPrice ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="需求交期" width="108">
          <template #default="{ row }">{{ row.prRequestedDate ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="供应商" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.supplierId" filterable placeholder="必选" style="width: 100%">
              <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="采购单价" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0.0001" :precision="4" :step="0.01" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="约定交期" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.promisedDate" type="date" value-format="YYYY-MM-DD" placeholder="默认=需求交期" style="width: 100%" clearable />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="convertDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmConvertToPo">生成采购订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 600; }
</style>
