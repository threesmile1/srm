<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { foundationApi, type OrgUnit, type Warehouse } from '../../api/foundation'
import { masterApi, type Supplier } from '../../api/master'
import { prApi } from '../../api/pr'
import { useMaterialRemoteSelect } from '../../composables/useMaterialRemoteSelect'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const warehouses = ref<Warehouse[]>([])
const suppliers = ref<Supplier[]>([])
const {
  materialOptions,
  materialLoading,
  remoteSearch: remoteSearchMaterials,
  prefetchInitial: prefetchMaterials,
} = useMaterialRemoteSelect()

const form = ref({
  procurementOrgId: null as number | null,
  requesterName: '',
  department: '',
  remark: '',
})

type LineRow = {
  materialId: number | null; warehouseId: number | null; supplierId: number | null
  qty: number; uom: string; unitPrice: number | null; requestedDate: string; remark: string
}
const lines = ref<LineRow[]>([
  { materialId: null, warehouseId: null, supplierId: null, qty: 1, uom: '', unitPrice: null, requestedDate: '', remark: '' },
])

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
  if (orgs.value.length) form.value.procurementOrgId = orgs.value[0].id
}

async function loadWarehouses() {
  if (!form.value.procurementOrgId) return
  warehouses.value = (await foundationApi.listWarehouses(form.value.procurementOrgId)).data
}

onMounted(async () => {
  await loadOrgs()
  await loadWarehouses()
  const [s] = await Promise.all([masterApi.listSuppliers(), prefetchMaterials()])
  suppliers.value = s.data
})

function addLine() {
  lines.value.push({ materialId: null, warehouseId: null, supplierId: null, qty: 1, uom: '', unitPrice: null, requestedDate: '', remark: '' })
}

function removeLine(i: number) { lines.value.splice(i, 1) }

async function save() {
  if (!form.value.procurementOrgId) { ElMessage.warning('请选择采购组织'); return }
  const validLines = lines.value.filter((l) => l.materialId)
  if (!validLines.length) { ElMessage.warning('请至少填写一行物料'); return }
  try {
    const r = await prApi.create({
      procurementOrgId: form.value.procurementOrgId,
      requesterName: form.value.requesterName || undefined,
      department: form.value.department || undefined,
      remark: form.value.remark || undefined,
      lines: validLines.map((l) => ({
        materialId: l.materialId!,
        warehouseId: l.warehouseId,
        supplierId: l.supplierId,
        qty: l.qty,
        uom: l.uom || undefined,
        unitPrice: l.unitPrice,
        requestedDate: l.requestedDate || undefined,
        remark: l.remark || undefined,
      })),
    })
    ElMessage.success('请购单创建成功: ' + r.data.prNo)
    router.push(`/pr/${r.data.id}`)
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e
      ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '创建失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">新建请购单</span>
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </div>
    <el-form label-width="100px" style="max-width: 800px">
      <el-form-item label="采购组织">
        <el-select v-model="form.procurementOrgId" @change="loadWarehouses" style="width: 100%">
          <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请人">
        <el-input v-model="form.requesterName" />
      </el-form-item>
      <el-form-item label="部门">
        <el-input v-model="form.department" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <div style="display: flex; justify-content: space-between; margin: 16px 0 8px">
      <span style="font-weight: 600">请购行明细</span>
      <el-button size="small" @click="addLine">添加行</el-button>
    </div>
    <el-table :data="lines" border size="small">
      <el-table-column label="物料" min-width="180">
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
      <el-table-column label="数量" width="100">
        <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="单位" width="80">
        <template #default="{ row }"><el-input v-model="row.uom" size="small" /></template>
      </el-table-column>
      <el-table-column label="参考单价" width="110">
        <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="供应商" min-width="160">
        <template #default="{ row }">
          <el-select v-model="row.supplierId" filterable clearable placeholder="可选" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="仓库" min-width="140">
        <template #default="{ row }">
          <el-select v-model="row.warehouseId" filterable clearable placeholder="可选" style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.code} ${w.name}`" :value="w.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="交期" width="140">
        <template #default="{ row }"><el-date-picker v-model="row.requestedDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" /></template>
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
</style>
