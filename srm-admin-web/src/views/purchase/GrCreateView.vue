<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit, type Warehouse } from '../../api/foundation'
import { purchaseApi, type PoDetail, type PoSummary } from '../../api/purchase'
import { executionApi } from '../../api/execution'
import { PROC_ORG_STORAGE_PREFIX } from '../../composables/usePersistedProcurementOrg'

const route = useRoute()
const router = useRouter()

const orgs = ref<OrgUnit[]>([])
const procurementOrgId = ref<number | null>(null)
const pos = ref<PoSummary[]>([])
const purchaseOrderId = ref<number | null>(null)
const poDetail = ref<PoDetail | null>(null)
const warehouses = ref<Warehouse[]>([])
const warehouseId = ref<number | null>(null)
const receiptDate = ref(new Date().toISOString().slice(0, 10))
const remark = ref('')
/** lineId -> received qty string */
const recvByLine = ref<Record<number, string>>({})
/** 采购订单行 id -> 发货通知 ASN 行 id（同订单多份通知时取通知 id 较大者） */
const polToAsnLineId = ref<Map<number, number>>(new Map())

const qOrg = computed(() => {
  const v = route.query.procurementOrgId
  return v != null && v !== '' ? Number(v) : null
})
const qPo = computed(() => {
  const v = route.query.poId
  return v != null && v !== '' ? Number(v) : null
})

function isNingboOrg(o: OrgUnit | null | undefined): boolean {
  if (!o) return false
  const code = (o.code ?? '').trim().toUpperCase()
  const name = (o.name ?? '').trim()
  const u9 = (o.u9OrgCode ?? '').trim()
  return code === 'NB' || name === '宁波公司' || u9 === '1001711275375071'
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
  if (procurementOrgId.value == null && orgs.value.length) {
    const key = PROC_ORG_STORAGE_PREFIX + 'gr-create'
    const raw = sessionStorage.getItem(key)
    let saved: number | null = null
    if (raw) {
      const n = Number(raw)
      if (!Number.isNaN(n) && orgs.value.some((o) => o.id === n)) saved = n
    }
    procurementOrgId.value = qOrg.value ?? saved ?? orgs.value[0].id
  }
}

async function loadPos() {
  pos.value = []
  purchaseOrderId.value = null
  poDetail.value = null
  if (procurementOrgId.value == null) return
  const r = await purchaseApi.list(procurementOrgId.value)
  pos.value = r.data.filter((p) => p.status === 'RELEASED')
  if (qPo.value != null) {
    const hit = pos.value.find((p) => p.id === qPo.value)
    if (hit) purchaseOrderId.value = hit.id
  } else if (pos.value.length === 1) {
    purchaseOrderId.value = pos.value[0].id
  }
}

async function loadWh() {
  warehouses.value = []
  warehouseId.value = null
  if (procurementOrgId.value == null) return
  const r = await foundationApi.listWarehouses(procurementOrgId.value)
  warehouses.value = r.data
  if (r.data.length) warehouseId.value = r.data[0].id
}

async function loadPolToAsnMapping() {
  polToAsnLineId.value = new Map()
  if (purchaseOrderId.value == null) return
  try {
    const r = await executionApi.listAsn(purchaseOrderId.value)
    const notices = r.data
    const best = new Map<number, { noticeId: number; asnLineId: number }>()
    for (const n of notices) {
      if (n.status !== 'SUBMITTED') continue
      for (const line of n.lines) {
        const pid = line.purchaseOrderLineId
        const cur = best.get(pid)
        if (!cur || n.id > cur.noticeId) {
          best.set(pid, { noticeId: n.id, asnLineId: line.id })
        }
      }
    }
    const m = new Map<number, number>()
    best.forEach((v, k) => m.set(k, v.asnLineId))
    polToAsnLineId.value = m
  } catch {
    polToAsnLineId.value = new Map()
  }
}

async function loadPoDetail() {
  poDetail.value = null
  recvByLine.value = {}
  polToAsnLineId.value = new Map()
  if (purchaseOrderId.value == null) return
  const r = await purchaseApi.get(purchaseOrderId.value)
  poDetail.value = r.data
  for (const l of r.data.lines) {
    const maxOpen = Math.max(0, Number(l.qty) - Number(l.receivedQty || 0))
    recvByLine.value[l.id] = maxOpen > 0 ? String(maxOpen) : '0'
  }
  await loadPolToAsnMapping()
}

watch(procurementOrgId, async (v) => {
  if (v != null) sessionStorage.setItem(PROC_ORG_STORAGE_PREFIX + 'gr-create', String(v))
  const o = orgs.value.find((x) => x.id === v)
  if (v != null && isNingboOrg(o)) {
    // 宁波禁止手工新建收货：静默回列表，避免与「收货单列表」混淆时反复弹窗
    router.replace('/purchase/receipts')
    return
  }
  await loadWh()
  await loadPos()
  await loadPoDetail()
})

watch(purchaseOrderId, () => {
  loadPoDetail()
})

onMounted(async () => {
  await loadOrgs()
  const o = orgs.value.find((x) => x.id === procurementOrgId.value)
  if (isNingboOrg(o ?? null)) {
    router.replace('/purchase/receipts')
    return
  }
  await loadWh()
  await loadPos()
  await loadPoDetail()
})

async function submit() {
  try {
    if (procurementOrgId.value == null || purchaseOrderId.value == null || warehouseId.value == null) {
      ElMessage.warning('请完整选择采购组织、订单与仓库')
      return
    }
    if (!poDetail.value) return
    const lines = poDetail.value.lines
      .map((l) => ({
        purchaseOrderLineId: l.id,
        receivedQty: Number(recvByLine.value[l.id] || 0),
        asnLineId: polToAsnLineId.value.get(l.id) ?? null,
      }))
      .filter((x) => x.receivedQty > 0)
    if (!lines.length) {
      ElMessage.warning('请至少一行填写大于 0 的收货数量')
      return
    }
    const missingAsn = lines.filter((x) => x.asnLineId == null)
    if (missingAsn.length) {
      ElMessage.warning('每行收货须关联 ASN：请先在门户确认订单行，并由供应商提交发货通知后再收货')
      return
    }
    const r = await executionApi.createGoodsReceipt({
      procurementOrgId: procurementOrgId.value,
      purchaseOrderId: purchaseOrderId.value,
      warehouseId: warehouseId.value,
      receiptDate: receiptDate.value,
      remark: remark.value || null,
      lines,
    })
    ElMessage.success('已创建 ' + r.data.grNo)
    router.push('/purchase/receipts')
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
    <div class="toolbar">
      <span class="title">新建收货单</span>
      <el-button @click="router.push('/purchase/receipts')">返回列表</el-button>
    </div>
    <el-form label-width="100px" style="max-width: 720px">
      <el-form-item label="采购组织">
        <el-select v-model="procurementOrgId" placeholder="选择" style="width: 100%">
          <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="采购订单">
        <el-select v-model="purchaseOrderId" placeholder="选择已发布订单" filterable style="width: 100%">
          <el-option v-for="p in pos" :key="p.id" :label="`${p.poNo} · ${p.supplierCode}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="仓库">
        <el-select v-model="warehouseId" placeholder="选择" style="width: 100%">
          <el-option v-for="w in warehouses" :key="w.id" :label="`${w.code} ${w.name}`" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="收货日期">
        <el-date-picker v-model="receiptDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" />
      </el-form-item>
    </el-form>

    <p v-if="poDetail" class="asn-hint">
      规则：须先由供应商在门户<strong>确认订单行</strong>并提交<strong>发货通知（ASN）</strong>，本页按通知自动匹配 ASN 行；<strong>无 ASN 的行不可收货</strong>。
      <template v-if="polToAsnLineId.size > 0">已匹配行可提交；订单仍有未收清时收货单会出现在「待收货」相关入口。</template>
    </p>

    <el-table v-if="poDetail" :data="poDetail.lines" stripe style="margin-top: 8px; max-width: 900px">
      <el-table-column prop="lineNo" label="行" width="50" />
      <el-table-column prop="materialCode" label="物料" width="110" />
      <el-table-column prop="materialName" label="名称" />
      <el-table-column label="发货通知" width="100">
        <template #default="{ row }">
          <span v-if="polToAsnLineId.has(row.id)" class="asn-ok">已匹配</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="qty" label="订购" width="80" />
      <el-table-column prop="receivedQty" label="已收" width="80" />
      <el-table-column label="本次收货" width="130">
        <template #default="{ row }">
          <el-input v-model="recvByLine[row.id]" />
        </template>
      </el-table-column>
    </el-table>

    <el-button type="primary" style="margin-top: 16px" @click="submit">提交</el-button>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.asn-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 8px;
  max-width: 900px;
  line-height: 1.5;
}
.asn-ok {
  color: var(--el-color-success);
}
</style>
