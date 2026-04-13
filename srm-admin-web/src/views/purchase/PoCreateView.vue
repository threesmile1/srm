<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { masterApi, type Supplier } from '../../api/master'
import { useMaterialRemoteSelect } from '../../composables/useMaterialRemoteSelect'
import { purchaseApi } from '../../api/purchase'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const suppliers = ref<Supplier[]>([])
const warehouses = ref<{ id: number; code: string; name: string }[]>([])
const {
  materialOptions,
  materialLoading,
  remoteSearch: remoteSearchMaterials,
  prefetchInitial: prefetchMaterials,
  getMaterial,
} = useMaterialRemoteSelect()

const procurementOrgId = ref<number | null>(null)
const supplierId = ref<number | null>(null)
const remark = ref('')
const lines = ref([
  { materialId: null as number | null, warehouseId: null as number | null, qty: 1, unitPrice: 0 },
])

async function loadBase() {
  const ledgers = await foundationApi.listLedgers()
  if (ledgers.data.length) {
    const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
    orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
    if (orgs.value.length) procurementOrgId.value = orgs.value[0].id
  }
  const [s] = await Promise.all([masterApi.listSuppliers(), prefetchMaterials()])
  suppliers.value = s.data
  if (suppliers.value.length) supplierId.value = suppliers.value[0].id
}

async function loadWh() {
  warehouses.value = []
  if (procurementOrgId.value == null) return
  const r = await foundationApi.listWarehouses(procurementOrgId.value)
  warehouses.value = r.data
  if (r.data.length) {
    lines.value.forEach((l) => {
      if (l.warehouseId == null) l.warehouseId = r.data[0].id
    })
  }
}

async function onOrgChange() {
  await loadWh()
}

function addLine() {
  const wh = warehouses.value[0]?.id ?? null
  lines.value.push({ materialId: null, warehouseId: wh, qty: 1, unitPrice: 0 })
}

async function submit() {
  try {
    if (procurementOrgId.value == null || supplierId.value == null) {
      ElMessage.warning('请选择采购组织与供应商')
      return
    }
    const ls = lines.value
      .filter((l) => l.materialId != null && l.warehouseId != null)
      .map((l) => ({
        materialId: l.materialId!,
        warehouseId: l.warehouseId!,
        qty: l.qty,
        unitPrice: l.unitPrice,
      }))
    if (!ls.length) {
      ElMessage.warning('请填写有效行')
      return
    }
    const r = await purchaseApi.create({
      procurementOrgId: procurementOrgId.value,
      supplierId: supplierId.value,
      currency: 'CNY',
      remark: remark.value || undefined,
      lines: ls,
    })
    ElMessage.success('已创建 ' + r.data.poNo)
    router.push(`/purchase/orders/${r.data.id}`)
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '创建失败')
  }
}

onMounted(async () => {
  await loadBase()
  await loadWh()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">新建采购订单</span>
      <el-button @click="$router.back()">返回</el-button>
    </div>
    <el-form label-width="120px" style="max-width: 720px">
      <el-form-item label="采购组织">
        <el-select v-model="procurementOrgId" style="width: 100%" @change="onOrgChange">
          <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商">
        <el-select v-model="supplierId" style="width: 100%" filterable>
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" />
      </el-form-item>
    </el-form>

    <el-table :data="lines" border style="max-width: 1200px">
      <el-table-column label="物料" width="400">
        <template #default="{ row }">
          <el-select
            v-model="row.materialId"
            filterable
            remote
            reserve-keyword
            placeholder="输入编码或名称搜索"
            :remote-method="remoteSearchMaterials"
            :loading="materialLoading"
            style="width: 100%"
          >
            <el-option v-for="m in materialOptions" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="规格" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getMaterial(row.materialId)?.specification?.trim() || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="单位" width="88">
        <template #default="{ row }">
          {{ getMaterial(row.materialId)?.uom?.trim() || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="仓库" width="160">
        <template #default="{ row }">
          <el-select v-model="row.warehouseId" placeholder="选择" style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.code" :value="w.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.qty" :min="0.0001" :step="1" />
        </template>
      </el-table-column>
      <el-table-column label="单价" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.unitPrice" :min="0" :step="0.01" />
        </template>
      </el-table-column>
    </el-table>
    <div style="margin: 12px 0">
      <el-button @click="addLine">增行</el-button>
      <el-button type="primary" @click="submit">保存草稿</el-button>
    </div>
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
</style>
