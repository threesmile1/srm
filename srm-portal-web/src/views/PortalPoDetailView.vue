<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalApi, type PoDetail, type PoLine } from '../api/portal'

const route = useRoute()
const router = useRouter()
const po = ref<PoDetail | null>(null)
const dialog = ref(false)
const currentLine = ref<PoLine | null>(null)
const form = ref({ confirmedQty: 0, promisedDate: '', remark: '' })

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
      <el-descriptions-item label="状态">{{ po.status }}</el-descriptions-item>
      <el-descriptions-item label="采购组织">{{ po.procurementOrgCode }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ po.currency }}</el-descriptions-item>
      <el-descriptions-item v-if="po.exportStatus" label="U9导出">{{ po.exportStatus }}</el-descriptions-item>
    </el-descriptions>

    <div v-if="po.status === 'RELEASED'" style="margin-top: 12px">
      <router-link :to="{ path: '/asn/new', query: { poId: String(po.id) } }">新建发货通知 (ASN)</router-link>
    </div>

    <el-table :data="po.lines" stripe style="margin-top: 16px">
      <el-table-column prop="lineNo" label="行" width="50" />
      <el-table-column prop="materialCode" label="物料" width="100" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column prop="qty" label="订购量" width="90" />
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
  </div>
</template>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
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
