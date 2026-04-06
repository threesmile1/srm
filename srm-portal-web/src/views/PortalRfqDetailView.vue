<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { usePortalAuthStore } from '../stores/portalAuth'
import { portalApi, type PortalRfqDetail } from '../api/portal'

const route = useRoute()
const router = useRouter()
const auth = usePortalAuthStore()
const { supplierId: sessionSupplierId } = storeToRefs(auth)

const detail = ref<PortalRfqDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)

const currency = ref('CNY')
const deliveryDays = ref<number | null>(7)
const validityDays = ref<number | null>(30)
const quoteRemark = ref('')

/** rfqLineId -> unit price */
const unitPrices = reactive<Record<number, number>>({})

const id = computed(() => Number(route.params.id))

function todayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const myInvitation = computed(() => {
  const sid = sessionSupplierId.value
  if (sid == null) return undefined
  return detail.value?.invitations?.find((i) => i.supplierId === sid)
})

const deadlineOk = computed(() => {
  const dl = detail.value?.deadline
  if (!dl) return true
  return dl >= todayStr()
})

const canSubmitQuote = computed(
  () =>
    detail.value?.status === 'PUBLISHED' &&
    !!myInvitation.value &&
    !myInvitation.value.responded &&
    deadlineOk.value,
)

watch(
  () => detail.value?.lines,
  (lines) => {
    if (!lines) return
    for (const l of lines) {
      if (unitPrices[l.id] === undefined) unitPrices[l.id] = 0
    }
  },
  { immediate: true },
)

async function load() {
  loading.value = true
  try {
    const r = await portalApi.getRfq(id.value)
    detail.value = r.data
  } catch (e: unknown) {
    detail.value = null
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '加载失败')
  } finally {
    loading.value = false
  }
}

async function submitQuote() {
  if (!detail.value || !canSubmitQuote.value) return
  const lines = detail.value.lines.map((l) => ({
    rfqLineId: l.id,
    unitPrice: unitPrices[l.id] ?? 0,
    remark: undefined as string | undefined,
  }))
  if (lines.some((x) => x.unitPrice <= 0)) {
    ElMessage.warning('请为每行填写大于 0 的单价')
    return
  }
  submitting.value = true
  try {
    await portalApi.submitRfqQuotation(id.value, {
      currency: currency.value || 'CNY',
      deliveryDays: deliveryDays.value ?? undefined,
      validityDays: validityDays.value ?? undefined,
      remark: quoteRemark.value.trim() || undefined,
      lines,
    })
    ElMessage.success('报价已提交')
    await load()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="head">
      <el-button link type="primary" @click="router.push('/rfq')">← 返回列表</el-button>
    </div>
    <template v-if="detail">
      <h2 class="title">{{ detail.rfqNo }} · {{ detail.title }}</h2>
      <p class="meta">
        <span>采购组织 {{ detail.procurementOrgCode }}</span>
        <span v-if="detail.deadline" class="deadline">报价截止 {{ detail.deadline }}</span>
        <span v-else class="deadline">未设截止日</span>
      </p>

      <el-alert
        v-if="detail.status === 'PUBLISHED' && !deadlineOk"
        type="warning"
        :closable="false"
        title="已超过报价截止日期，无法提交。"
        style="margin: 12px 0"
      />
      <el-alert
        v-else-if="detail.status === 'PUBLISHED' && myInvitation?.responded"
        type="success"
        :closable="false"
        title="您已提交报价，请等待采购方定标。"
        style="margin: 12px 0"
      />
      <el-alert
        v-else-if="detail.status === 'AWARDED'"
        type="info"
        :closable="false"
        title="本询价已定标。"
        style="margin: 12px 0"
      />

      <h3 class="sub">询价行</h3>
      <el-table :data="detail.lines" border size="small" style="margin-bottom: 20px">
        <el-table-column prop="lineNo" label="#" width="50" />
        <el-table-column label="物料" min-width="200">
          <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column prop="uom" label="单位" width="72" />
        <el-table-column prop="specification" label="规格" show-overflow-tooltip />
        <el-table-column v-if="canSubmitQuote" label="单价（必填）" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="unitPrices[row.id]"
              :min="0"
              :precision="4"
              :step="0.01"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
      </el-table>

      <template v-if="canSubmitQuote">
        <h3 class="sub">提交报价</h3>
        <el-form label-width="100px" class="quote-form" @submit.prevent>
          <el-form-item label="币别">
            <el-input v-model="currency" style="width: 120px" />
          </el-form-item>
          <el-form-item label="交货期(天)">
            <el-input-number v-model="deliveryDays" :min="0" :step="1" />
          </el-form-item>
          <el-form-item label="报价有效(天)">
            <el-input-number v-model="validityDays" :min="0" :step="1" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="quoteRemark" type="textarea" :rows="2" style="max-width: 480px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submitQuote">提交报价</el-button>
          </el-form-item>
        </el-form>
      </template>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: none;
  margin: 0;
  padding: 0;
}
.head {
  margin-bottom: 8px;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.deadline {
  color: var(--el-color-warning-dark-2);
}
.sub {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
}
.quote-form {
  max-width: 560px;
}
</style>
