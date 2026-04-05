<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalApi, type PoDetail, type PoSummary } from '../api/portal'

const route = useRoute()
const router = useRouter()

const pos = ref<PoSummary[]>([])
const purchaseOrderId = ref<number | null>(null)
const poDetail = ref<PoDetail | null>(null)
const shipDate = ref(new Date().toISOString().slice(0, 10))
const etaDate = ref<string | null>(null)
const carrier = ref('')
const trackingNo = ref('')
const remark = ref('')
const shipByLine = ref<Record<number, string>>({})

const qPo = computed(() => {
  const v = route.query.poId
  return v != null && v !== '' ? Number(v) : null
})

async function loadPos() {
  const r = await portalApi.listPos()
  pos.value = r.data.filter((p) => p.status === 'RELEASED')
  if (qPo.value != null) {
    const hit = pos.value.find((p) => p.id === qPo.value)
    if (hit) purchaseOrderId.value = hit.id
  } else if (pos.value.length === 1) {
    purchaseOrderId.value = pos.value[0].id
  }
}

async function loadPo() {
  poDetail.value = null
  shipByLine.value = {}
  if (purchaseOrderId.value == null) return
  const r = await portalApi.getPo(purchaseOrderId.value)
  poDetail.value = r.data
  for (const l of r.data.lines) {
    const open = Math.max(0, Number(l.qty) - Number(l.receivedQty || 0))
    shipByLine.value[l.id] = open > 0 ? String(open) : '0'
  }
}

watch(purchaseOrderId, () => {
  loadPo()
})

onMounted(async () => {
  await loadPos()
  await loadPo()
})

async function submit() {
  if (purchaseOrderId.value == null || !poDetail.value) {
    ElMessage.warning('请选择订单')
    return
  }
  const lines = poDetail.value.lines
    .map((l) => ({
      purchaseOrderLineId: l.id,
      shipQty: Number(shipByLine.value[l.id] || 0),
    }))
    .filter((x) => x.shipQty > 0)
  if (!lines.length) {
    ElMessage.warning('请至少一行填写大于 0 的发货数量')
    return
  }
  try {
    await portalApi.createAsn({
      purchaseOrderId: purchaseOrderId.value,
      shipDate: shipDate.value,
      etaDate: etaDate.value || null,
      carrier: carrier.value || null,
      trackingNo: trackingNo.value || null,
      remark: remark.value || null,
      lines,
    })
    ElMessage.success('已提交 ASN')
    router.push('/asn')
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="head">
      <h2 class="title">新建发货通知</h2>
      <router-link to="/asn">返回列表</router-link>
    </div>
    <el-form label-width="100px" style="margin-top: 16px; max-width: 640px">
      <el-form-item label="采购订单">
        <el-select v-model="purchaseOrderId" placeholder="选择已发布订单" filterable style="width: 100%">
          <el-option v-for="p in pos" :key="p.id" :label="`${p.poNo}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="发货日期">
        <el-date-picker v-model="shipDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="预计到货">
        <el-date-picker v-model="etaDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" clearable />
      </el-form-item>
      <el-form-item label="承运商">
        <el-input v-model="carrier" />
      </el-form-item>
      <el-form-item label="运单号">
        <el-input v-model="trackingNo" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" />
      </el-form-item>
    </el-form>

    <el-table v-if="poDetail" :data="poDetail.lines" stripe style="margin-top: 8px">
      <el-table-column prop="lineNo" label="行" width="50" />
      <el-table-column prop="materialCode" label="物料" width="110" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column prop="qty" label="订购" width="80" />
      <el-table-column prop="receivedQty" label="已收" width="80" />
      <el-table-column label="发货量" width="120">
        <template #default="{ row }">
          <el-input v-model="shipByLine[row.id]" />
        </template>
      </el-table-column>
    </el-table>

    <el-button type="primary" style="margin-top: 16px" @click="submit">提交</el-button>
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
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
</style>
