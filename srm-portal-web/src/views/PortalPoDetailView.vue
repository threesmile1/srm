<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalApi, type PoDetail, type PoLine } from '../api/portal'

const route = useRoute()
const router = useRouter()
const po = ref<PoDetail | null>(null)

const poStatusMap: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审核',
  RELEASED: '已发布',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

function poStatusLabel(s: string | undefined) {
  if (!s) return '—'
  return poStatusMap[s] ?? s
}
const dialog = ref(false)
const batchDialog = ref(false)
const currentLine = ref<PoLine | null>(null)
const form = ref({ confirmedQty: 0, promisedDate: '', remark: '' })
const batchForm = ref({ promisedDate: '' })

async function load() {
  const id = Number(route.params.id)
  const r = await portalApi.getPo(id)
  po.value = r.data
}

function openConfirm(line: PoLine) {
  currentLine.value = line
  form.value = {
    confirmedQty: Number(line.qty),
    promisedDate: line.promisedDate || '',
    remark: line.supplierRemark || '',
  }
  dialog.value = true
}

async function saveConfirm() {
  if (!currentLine.value) return
  try {
    await portalApi.confirmLine(currentLine.value.id, {
      confirmedQty: form.value.confirmedQty,
      promisedDate: form.value.promisedDate || null,
      supplierRemark: form.value.remark || undefined,
    })
    ElMessage.success('已确认')
    dialog.value = false
    await load()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '失败')
  }
}

function openBatchConfirm() {
  batchForm.value = { promisedDate: '' }
  batchDialog.value = true
}

async function saveBatchConfirm() {
  if (!po.value) return
  if (!batchForm.value.promisedDate) {
    ElMessage.error('请填写承诺交期')
    return
  }
  const lines = po.value.lines.filter((l) => !l.confirmedAt)
  if (lines.length === 0) {
    ElMessage.info('没有需要确认的行')
    batchDialog.value = false
    return
  }

  let ok = 0
  let fail = 0
  for (const line of lines) {
    try {
      await portalApi.confirmLine(line.id, {
        confirmedQty: Number(line.qty),
        promisedDate: batchForm.value.promisedDate,
      })
      ok++
    } catch {
      fail++
    }
  }
  if (fail === 0) ElMessage.success(`批量确认成功：${ok} 条`)
  else ElMessage.warning(`批量确认完成：成功 ${ok} 条，失败 ${fail} 条`)

  batchDialog.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <div v-if="po" class="page">
    <div class="head">
      <el-button text type="primary" @click="router.push('/pos')">← 返回列表</el-button>
      <h2 class="title">订单详情</h2>
    </div>
    <el-descriptions :column="2" border style="margin-top: 16px">
      <el-descriptions-item label="订单号">{{ po.poNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ poStatusLabel(po.status) }}</el-descriptions-item>
      <el-descriptions-item label="采购组织">{{ po.procurementOrgCode }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ po.currency }}</el-descriptions-item>
      <el-descriptions-item v-if="po.exportStatus" label="U9导出">{{ po.exportStatus }}</el-descriptions-item>
    </el-descriptions>

    <div v-if="po.status === 'RELEASED'" style="margin-top: 12px; display: flex; align-items: center; gap: 16px">
      <router-link :to="{ path: '/asn/new', query: { poId: String(po.id) } }">新建发货通知 (ASN)</router-link>
      <el-button type="primary" @click="openBatchConfirm">确认</el-button>
    </div>

    <el-table :data="po.lines" stripe style="margin-top: 16px">
      <el-table-column prop="lineNo" label="行" width="50" />
      <el-table-column prop="materialCode" label="物料" width="100" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column prop="materialSpec" label="规格" width="160" show-overflow-tooltip />
      <el-table-column prop="qty" label="订购量" width="90" />
      <el-table-column prop="unitPrice" label="单价" width="100" />
      <el-table-column prop="amount" label="金额" width="110" />
      <el-table-column prop="requestedDate" label="要求交货日期" width="120" />
      <el-table-column prop="receivedQty" label="已收" width="80" />
      <el-table-column prop="confirmedQty" label="确认量" width="90" />
      <el-table-column prop="promisedDate" label="承诺交期" width="110" />
      <el-table-column label="操作" width="100" v-if="po.status === 'RELEASED'">
        <template #default="{ row }">
          <el-button link type="primary" @click="openConfirm(row)">确认行</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="订单行确认" width="420px">
      <el-form label-width="100px">
        <el-form-item label="确认数量">
          <el-input-number v-model="form.confirmedQty" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="承诺交期">
          <el-date-picker v-model="form.promisedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="saveConfirm">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialog" title="批量确认" width="420px">
      <el-form label-width="100px">
        <el-form-item label="承诺交期">
          <el-date-picker v-model="batchForm.promisedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <div style="color: var(--el-text-color-secondary); font-size: 12px">
            将对所有未确认行自动按订购量确认，并批量填写相同的承诺交期。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBatchConfirm">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style scoped>
.page {
  max-width: none;
  margin: 0;
  padding: 0;
}
.head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
</style>

