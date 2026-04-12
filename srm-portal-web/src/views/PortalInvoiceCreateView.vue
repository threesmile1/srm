<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { api } from '../api/http'
import { portalInvoiceApi, type BillablePoLine } from '../api/invoice'

const router = useRouter()

type OrgOption = { id: number; code: string; name: string }
const orgs = ref<OrgOption[]>([])

const form = ref({
  procurementOrgId: null as number | null,
  invoiceDate: '',
  currency: 'CNY',
  taxAmount: 0,
  remark: '',
  invoiceKind: 'ORDINARY_VAT' as 'ORDINARY_VAT' | 'SPECIAL_VAT',
  vatInvoiceCode: '',
  vatInvoiceNumber: '',
})

/** 甄云类：主路径为关联 PO 行；可选无订单手工行 */
type LineRow = {
  source: 'PO' | 'MANUAL'
  purchaseOrderLineId: number | null
  purchaseOrderId: number | null
  poLabel: string
  materialCode: string
  materialName: string
  qty: number
  /** PO 行：可开票上限（已收 − 已开票） */
  maxQty: number | null
  unitPrice: number
  taxRate: number
}

const lines = ref<LineRow[]>([])
const billableRows = ref<BillablePoLine[]>([])
const pickerOpen = ref(false)
const billableLoading = ref(false)
/** 待提交成功后上传的发票扫描件（PDF/图片等） */
const pendingUploadList = ref<UploadUserFile[]>([])

const DEFAULT_TAX = 13

function onAttachmentChange(_file: UploadFile, uploadFiles: UploadFiles) {
  pendingUploadList.value = [...uploadFiles]
}

async function loadOrgs() {
  const ledgers = (await api.get('/api/v1/ledgers')).data
  if (ledgers.length) {
    const ou = (await api.get(`/api/v1/ledgers/${ledgers[0].id}/org-units`)).data
    orgs.value = ou.filter((o: { orgType: string }) => o.orgType === 'PROCUREMENT')
    if (orgs.value.length) form.value.procurementOrgId = orgs.value[0].id
  }
}

async function loadBillableLines() {
  if (form.value.procurementOrgId == null) {
    billableRows.value = []
    return
  }
  billableLoading.value = true
  try {
    billableRows.value = (await portalInvoiceApi.billableLines(form.value.procurementOrgId)).data
  } catch {
    billableRows.value = []
  } finally {
    billableLoading.value = false
  }
}

watch(() => form.value.procurementOrgId, () => {
  loadBillableLines()
})

onMounted(async () => {
  await loadOrgs()
  await loadBillableLines()
})

function openPicker() {
  if (!form.value.procurementOrgId) {
    ElMessage.warning('请先选择采购组织')
    return
  }
  loadBillableLines()
  pickerOpen.value = true
}

function num(s: string) {
  return Number.parseFloat(s)
}

function addFromBillable(row: BillablePoLine) {
  if (lines.value.some((l) => l.purchaseOrderLineId === row.purchaseOrderLineId)) {
    ElMessage.warning('该行已在发票明细中')
    return
  }
  const rem = num(row.remainingInvoiceableQty)
  if (!(rem > 0)) return
  lines.value.push({
    source: 'PO',
    purchaseOrderLineId: row.purchaseOrderLineId,
    purchaseOrderId: row.purchaseOrderId,
    poLabel: `${row.poNo} 行${row.lineNo}`,
    materialCode: row.materialCode,
    materialName: row.materialName,
    qty: rem,
    maxQty: rem,
    unitPrice: num(row.unitPrice),
    taxRate: DEFAULT_TAX,
  })
  ElMessage.success('已加入明细，可调整数量（不超过可开票数量）')
}

function addManualLine() {
  lines.value.push({
    source: 'MANUAL',
    purchaseOrderLineId: null,
    purchaseOrderId: null,
    poLabel: '—',
    materialCode: '',
    materialName: '',
    qty: 1,
    maxQty: null,
    unitPrice: 0,
    taxRate: DEFAULT_TAX,
  })
}

function removeLine(i: number) {
  lines.value.splice(i, 1)
}

async function save() {
  if (!form.value.procurementOrgId) {
    ElMessage.warning('请选择采购组织')
    return
  }
  if (!form.value.invoiceDate) {
    ElMessage.warning('请填写开票日期')
    return
  }
  const validLines = lines.value.filter((l) => {
    if (l.source === 'PO') {
      return l.materialCode && l.qty > 0
    }
    return l.materialCode && l.qty > 0
  })
  if (!validLines.length) {
    ElMessage.warning('请从可对账订单行添加明细，或使用「手工行」填写物料与数量')
    return
  }
  for (const l of validLines) {
    if (l.source === 'PO' && l.maxQty != null && l.qty > l.maxQty + 1e-9) {
      ElMessage.error(`明细「${l.poLabel}」开票数量不能超过可开票数量 ${l.maxQty}`)
      return
    }
  }
  try {
    const r = await portalInvoiceApi.create({
      procurementOrgId: form.value.procurementOrgId,
      invoiceDate: form.value.invoiceDate,
      currency: form.value.currency,
      taxAmount: form.value.taxAmount,
      remark: form.value.remark || undefined,
      invoiceKind: form.value.invoiceKind,
      vatInvoiceCode: form.value.vatInvoiceCode.trim() || undefined,
      vatInvoiceNumber: form.value.vatInvoiceNumber.trim() || undefined,
      lines: validLines.map((l) => ({
        materialCode: l.materialCode,
        materialName: l.materialName || undefined,
        qty: l.qty,
        unitPrice: l.unitPrice,
        taxRate: l.taxRate || undefined,
        purchaseOrderId: l.purchaseOrderId ?? undefined,
        purchaseOrderLineId: l.purchaseOrderLineId ?? undefined,
      })),
    })
    const invId = r.data.id
    let uploadFail = 0
    for (const uf of pendingUploadList.value) {
      const raw = uf.raw
      if (!(raw instanceof File)) continue
      try {
        await portalInvoiceApi.uploadAttachment(invId, raw)
      } catch {
        uploadFail++
      }
    }
    if (uploadFail > 0) {
      ElMessage.warning(`发票已提交 ${r.data.invoiceNo}，但有 ${uploadFail} 个附件上传失败，请检查网络后重试或联系采购员`)
    } else {
      ElMessage.success('发票提交成功: ' + r.data.invoiceNo)
    }
    pendingUploadList.value = []
    router.push('/invoices')
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e
      ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '提交失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">提交发票</span>
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="save">提交</el-button>
    </div>
    <p class="intro">
      与甄云类 SRM 一致：请从<strong>可对账订单行</strong>勾选（已发布或已关闭订单、已收货且仍有可开票余额）。
      若为空请核对：当前登录供应商是否对应 01.0001、采购组织是否与订单一致；订单仅草稿/未发布时不会出现。
      票面信息以明细与税务代码/号码为准；需要时可另附<strong>发票附件</strong>（扫描件或 PDF）。
    </p>
    <el-form label-width="100px" style="max-width: 700px">
      <el-form-item label="采购组织">
        <el-select v-model="form.procurementOrgId" style="width: 100%" @change="loadBillableLines">
          <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="开票日期">
        <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="币种">
        <el-input v-model="form.currency" style="width: 120px" />
      </el-form-item>
      <el-form-item label="票种">
        <el-radio-group v-model="form.invoiceKind">
          <el-radio label="ORDINARY_VAT">增值税普票</el-radio>
          <el-radio label="SPECIAL_VAT">增值税专票</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="发票代码">
        <el-input v-model="form.vatInvoiceCode" maxlength="12" placeholder="10–12 位数字，专票必填" style="max-width: 280px" />
      </el-form-item>
      <el-form-item label="发票号码">
        <el-input v-model="form.vatInvoiceNumber" maxlength="20" placeholder="8–20 位数字，专票必填" style="max-width: 280px" />
      </el-form-item>
      <el-form-item label="税额">
        <el-input-number v-model="form.taxAmount" :min="0" :precision="2" />
        <span class="hint">明细填写税率(%)后，系统按行汇总税额</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="发票附件">
        <el-upload
          :auto-upload="false"
          :limit="8"
          :on-change="onAttachmentChange"
          :on-exceed="() => ElMessage.warning('最多选择 8 个文件')"
          multiple
        >
          <el-button type="primary" plain>选取文件</el-button>
          <template #tip>
            <div class="upload-tip">可选：PDF、图片等扫描件，单个不超过 10MB；点击「提交」后随发票一并上传。</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <div class="block-head">
      <span class="block-title">发票明细</span>
      <div class="block-actions">
        <el-button type="primary" @click="openPicker">从订单行选择</el-button>
        <el-button @click="addManualLine">手工添加一行</el-button>
      </div>
    </div>
    <el-table v-if="lines.length" :data="lines" border size="small">
      <el-table-column prop="poLabel" label="来源" width="150" />
      <el-table-column label="物料编码" width="130">
        <template #default="{ row }">
          <el-input v-if="row.source === 'MANUAL'" v-model="row.materialCode" size="small" placeholder="编码" />
          <span v-else>{{ row.materialCode }}</span>
        </template>
      </el-table-column>
      <el-table-column label="物料名称" min-width="140">
        <template #default="{ row }">
          <el-input v-if="row.source === 'MANUAL'" v-model="row.materialName" size="small" placeholder="名称" />
          <span v-else>{{ row.materialName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="130">
        <template #default="{ row }">
          <el-input-number
            v-model="row.qty"
            :min="0.0001"
            :max="row.maxQty ?? undefined"
            :precision="4"
            size="small"
            controls-position="right"
            style="width: 120px"
          />
        </template>
      </el-table-column>
      <el-table-column label="单价" width="120">
        <template #default="{ row }">
          <el-input-number
            v-if="row.source === 'MANUAL'"
            v-model="row.unitPrice"
            :min="0"
            :precision="4"
            size="small"
            controls-position="right"
            style="width: 110px"
          />
          <span v-else>{{ row.unitPrice }}</span>
        </template>
      </el-table-column>
      <el-table-column label="税率(%)" width="100">
        <template #default="{ row }">
          <el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small" controls-position="right" />
        </template>
      </el-table-column>
      <el-table-column label="" width="50">
        <template #default="{ $index }">
          <el-button :icon="Delete" link type="danger" @click="removeLine($index)" />
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="请点击「从订单行选择」添加明细" :image-size="80" />

    <el-dialog v-model="pickerOpen" title="选择可对账订单行" width="920px" destroy-on-close>
      <p class="dialog-tip">展示：已发布或已关闭订单、已收货、且（已收 − 已提交/已确认开票）仍有余量的行。</p>
      <el-table v-loading="billableLoading" :data="billableRows" border size="small" max-height="420">
        <template #empty>
          <span class="muted">暂无数据。请确认该组织下已有已收货的发布订单。</span>
        </template>
        <el-table-column prop="poNo" label="订单号" width="150" />
        <el-table-column prop="lineNo" label="行" width="56" />
        <el-table-column prop="materialCode" label="物料编码" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="receivedQty" label="已收" width="90" />
        <el-table-column prop="invoicedQty" label="已开票" width="90" />
        <el-table-column prop="remainingInvoiceableQty" label="可开票" width="90" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column prop="uom" label="单位" width="64" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="num(row.remainingInvoiceableQty) > 0"
              link
              type="primary"
              @click="addFromBillable(row)"
            >
              添加
            </el-button>
            <el-tag v-else type="info" size="small">已开齐</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
.title { font-size: 18px; font-weight: 600; }
.intro { margin: 0 0 16px; font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.5; max-width: 720px; }
.hint { margin-left: 12px; font-size: 12px; color: var(--el-text-color-secondary); }
.block-head { display: flex; align-items: center; justify-content: space-between; margin: 20px 0 12px; }
.block-title { font-weight: 600; font-size: 15px; }
.block-actions { display: flex; gap: 8px; }
.dialog-tip { margin: 0 0 12px; font-size: 13px; color: var(--el-text-color-secondary); }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
.upload-tip { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.4; margin-top: 6px; max-width: 520px; }
</style>
