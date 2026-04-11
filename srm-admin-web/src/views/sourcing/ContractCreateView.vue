<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { masterApi, type Material, type Supplier } from '../../api/master'
import { contractApi } from '../../api/contract'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const procurementOrgId = ref<number | null>(null)
usePersistedProcurementOrg(procurementOrgId, orgs, 'contract-list')

const suppliers = ref<Supplier[]>([])
const materials = ref<Material[]>([])
const supplierId = ref<number | null>(null)
const title = ref('')
const contractType = ref('FRAMEWORK')
const startDate = ref<string | null>(null)
const endDate = ref<string | null>(null)
const currency = ref('CNY')
const remark = ref('')
const lines = ref<
  { materialId: number | null; materialDesc: string; qty: number | null; uom: string; unitPrice: number | null }[]
>([{ materialId: null, materialDesc: '', qty: 1, uom: 'PCS', unitPrice: null }])

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadMaster() {
  const [s, m] = await Promise.all([masterApi.listSuppliers(), masterApi.listAllMaterialsForSelect()])
  suppliers.value = s.data
  materials.value = m.data
  if (s.data.length) supplierId.value = s.data[0].id
}

function addLine() {
  lines.value.push({ materialId: null, materialDesc: '', qty: 1, uom: 'PCS', unitPrice: null })
}

function removeLine(i: number) {
  if (lines.value.length > 1) lines.value.splice(i, 1)
}

async function submit() {
  if (procurementOrgId.value == null || supplierId.value == null) {
    ElMessage.warning('请选择采购组织与供应商')
    return
  }
  if (!title.value.trim()) {
    ElMessage.warning('请填写合同标题')
    return
  }
  const ls = lines.value.map((l) => ({
    materialId: l.materialId ?? undefined,
    materialDesc: l.materialDesc?.trim() || undefined,
    qty: l.qty ?? undefined,
    uom: l.uom?.trim() || undefined,
    unitPrice: l.unitPrice ?? undefined,
  }))
  if (!ls.length) {
    ElMessage.warning('至少一行')
    return
  }
  try {
    const r = await contractApi.create({
      supplierId: supplierId.value,
      procurementOrgId: procurementOrgId.value,
      title: title.value.trim(),
      contractType: contractType.value || undefined,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      currency: currency.value || undefined,
      remark: remark.value.trim() || undefined,
      lines: ls,
    })
    ElMessage.success('已创建 ' + r.data.contractNo)
    router.push(`/sourcing/contracts/${r.data.id}`)
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '创建失败')
  }
}

onMounted(async () => {
  await loadOrgs()
  await loadMaster()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">新建合同</span>
      <el-button @click="router.back()">返回</el-button>
    </div>
    <el-form label-width="120px" class="form-block">
      <el-form-item label="采购组织">
        <el-select v-model="procurementOrgId" style="width: 100%; max-width: 400px">
          <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商">
        <el-select v-model="supplierId" filterable style="width: 100%; max-width: 400px">
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="title" style="max-width: 480px" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="contractType" style="width: 200px">
          <el-option label="框架协议" value="FRAMEWORK" />
          <el-option label="单笔" value="SPOT" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效区间">
        <el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始" />
        <span class="dash">—</span>
        <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束" />
      </el-form-item>
      <el-form-item label="币别">
        <el-input v-model="currency" style="width: 120px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" :rows="2" style="max-width: 560px" />
      </el-form-item>
    </el-form>

    <div class="lines-head">
      <span class="sub-title">合同行</span>
      <el-button size="small" @click="addLine">增行</el-button>
    </div>
    <el-table :data="lines" border style="max-width: 960px">
      <el-table-column label="物料（可选）" min-width="200">
        <template #default="{ row }">
          <el-select v-model="row.materialId" clearable filterable placeholder="不选可填描述" style="width: 100%">
            <el-option v-for="m in materials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="物料说明" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.materialDesc" placeholder="无物料时填写" />
        </template>
      </el-table-column>
      <el-table-column label="数量" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.qty" :min="0" :step="1" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="单位" width="90">
        <template #default="{ row }">
          <el-input v-model="row.uom" />
        </template>
      </el-table-column>
      <el-table-column label="单价" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.unitPrice" :min="0" :precision="4" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="" width="56" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeLine($index)">删</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="footer-actions">
      <el-button type="primary" @click="submit">保存（草稿）</el-button>
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
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.dash {
  margin: 0 8px;
  color: var(--el-text-color-secondary);
}
.lines-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0 8px;
}
.sub-title {
  font-weight: 600;
}
.footer-actions {
  margin-top: 20px;
}
</style>
