<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { masterApi, type Material, type Supplier } from '../../api/master'
import { rfqApi } from '../../api/rfq'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const procurementOrgId = ref<number | null>(null)
usePersistedProcurementOrg(procurementOrgId, orgs, 'rfq-list')

const materials = ref<Material[]>([])
const suppliers = ref<Supplier[]>([])
const title = ref('')
const deadline = ref<string | null>(null)
const remark = ref('')
const supplierIds = ref<number[]>([])
const lines = ref<{ materialId: number | null; qty: number; uom: string; specification: string }[]>([
  { materialId: null, qty: 1, uom: '', specification: '' },
])

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadMaster() {
  const [m, s] = await Promise.all([masterApi.listAllMaterialsForSelect(), masterApi.listSuppliers()])
  materials.value = m.data
  suppliers.value = s.data
}

function addLine() {
  lines.value.push({ materialId: null, qty: 1, uom: '', specification: '' })
}

function removeLine(i: number) {
  if (lines.value.length > 1) lines.value.splice(i, 1)
}

function onMatChange(row: (typeof lines.value)[0], id: unknown) {
  const mid = typeof id === 'number' ? id : null
  const m = materials.value.find((x) => x.id === mid)
  if (m && !String(row.uom || '').trim()) row.uom = m.uom
}

async function submit() {
  if (procurementOrgId.value == null) {
    ElMessage.warning('请选择采购组织')
    return
  }
  if (!title.value.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!supplierIds.value.length) {
    ElMessage.warning('请至少选择一家受邀供应商（发布前必填）')
    return
  }
  const ls = lines.value
    .filter((l) => l.materialId != null)
    .map((l) => {
      const m = materials.value.find((x) => x.id === l.materialId)
      return {
        materialId: l.materialId!,
        qty: l.qty,
        uom: l.uom?.trim() || m?.uom || 'PCS',
        specification: l.specification?.trim() || undefined,
      }
    })
  if (!ls.length) {
    ElMessage.warning('请至少一行有效物料')
    return
  }
  try {
    const r = await rfqApi.create({
      procurementOrgId: procurementOrgId.value,
      title: title.value.trim(),
      deadline: deadline.value || undefined,
      remark: remark.value.trim() || undefined,
      lines: ls,
      supplierIds: supplierIds.value,
    })
    ElMessage.success('已创建 ' + r.data.rfqNo)
    router.push(`/sourcing/rfq/${r.data.id}`)
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
      <span class="title">新建询价</span>
      <el-button @click="router.back()">返回</el-button>
    </div>
    <el-form label-width="120px" class="form-block">
      <el-form-item label="采购组织">
        <el-select v-model="procurementOrgId" style="width: 100%; max-width: 400px">
          <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="title" placeholder="询价主题" style="max-width: 480px" />
      </el-form-item>
      <el-form-item label="报价截止">
        <el-date-picker v-model="deadline" type="date" value-format="YYYY-MM-DD" placeholder="可选" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" :rows="2" style="max-width: 560px" />
      </el-form-item>
      <el-form-item label="邀请供应商">
        <el-select
          v-model="supplierIds"
          multiple
          filterable
          placeholder="多选"
          style="width: 100%; max-width: 560px"
        >
          <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="lines-head">
      <span class="sub-title">询价行</span>
      <el-button size="small" @click="addLine">增行</el-button>
    </div>
    <el-table :data="lines" border style="max-width: 920px">
      <el-table-column label="物料" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.materialId"
            filterable
            placeholder="选择"
            style="width: 100%"
            @change="(id: number | undefined) => onMatChange(row, id)"
          >
            <el-option v-for="m in materials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.qty" :min="0.0001" :step="1" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="单位" width="100">
        <template #default="{ row }">
          <el-input v-model="row.uom" placeholder="默认取物料" />
        </template>
      </el-table-column>
      <el-table-column label="规格说明" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.specification" />
        </template>
      </el-table-column>
      <el-table-column label="" width="72" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeLine($index)">删</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="footer-actions">
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
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.form-block {
  margin-bottom: 8px;
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
