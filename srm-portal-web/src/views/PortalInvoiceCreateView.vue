<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { api } from '../api/http'
import { portalInvoiceApi } from '../api/invoice'

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

type LineRow = {
  materialCode: string; materialName: string
  qty: number; unitPrice: number; taxRate: number
  purchaseOrderId: number | null
}
const lines = ref<LineRow[]>([
  { materialCode: '', materialName: '', qty: 1, unitPrice: 0, taxRate: 0, purchaseOrderId: null },
])

onMounted(async () => {
  const ledgers = (await api.get('/api/v1/ledgers')).data
  if (ledgers.length) {
    const ou = (await api.get(`/api/v1/ledgers/${ledgers[0].id}/org-units`)).data
    orgs.value = ou.filter((o: any) => o.orgType === 'PROCUREMENT')
    if (orgs.value.length) form.value.procurementOrgId = orgs.value[0].id
  }
})

function addLine() {
  lines.value.push({ materialCode: '', materialName: '', qty: 1, unitPrice: 0, taxRate: 0, purchaseOrderId: null })
}

function removeLine(i: number) { lines.value.splice(i, 1) }

async function save() {
  if (!form.value.procurementOrgId) { ElMessage.warning('请选择采购组织'); return }
  if (!form.value.invoiceDate) { ElMessage.warning('请填写开票日期'); return }
  const validLines = lines.value.filter((l) => l.materialCode && l.qty > 0)
  if (!validLines.length) { ElMessage.warning('请至少填写一行明细'); return }
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
        materialName: l.materialName,
        qty: l.qty,
        unitPrice: l.unitPrice,
        taxRate: l.taxRate || undefined,
        purchaseOrderId: l.purchaseOrderId || undefined,
      })),
    })
    ElMessage.success('发票提交成功: ' + r.data.invoiceNo)
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
    <el-form label-width="100px" style="max-width: 700px">
      <el-form-item label="采购组织">
        <el-select v-model="form.procurementOrgId" style="width: 100%">
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
        <span class="hint">填写明细税率(%)后提交，系统按行汇总税额（与甄云类价税明细一致）</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <div style="display: flex; justify-content: space-between; margin: 16px 0 8px">
      <span style="font-weight: 600">发票明细行</span>
      <el-button size="small" @click="addLine">添加行</el-button>
    </div>
    <el-table :data="lines" border size="small">
      <el-table-column label="物料编码" width="140">
        <template #default="{ row }"><el-input v-model="row.materialCode" size="small" /></template>
      </el-table-column>
      <el-table-column label="物料名称" min-width="160">
        <template #default="{ row }"><el-input v-model="row.materialName" size="small" /></template>
      </el-table-column>
      <el-table-column label="数量" width="100">
        <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="单价" width="110">
        <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="税率(%)" width="100">
        <template #default="{ row }"><el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="" width="50">
        <template #default="{ $index }">
          <el-button :icon="Delete" link type="danger" @click="removeLine($index)" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
.hint { margin-left: 12px; font-size: 12px; color: var(--el-text-color-secondary); }
</style>
