<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { executionApi, type GrSummary } from '../../api/execution'
import { masterApi, type Supplier } from '../../api/master'
import { qualityApi, type CorrectiveActionItem, type InspectionItem } from '../../api/quality'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'quality-collab')

const activeTab = ref('inspections')
const inspections = ref<InspectionItem[]>([])
const cas = ref<CorrectiveActionItem[]>([])
const grList = ref<GrSummary[]>([])
const suppliers = ref<Supplier[]>([])

const inspDialog = ref(false)
const caDialog = ref(false)
const inspForm = ref({
  grId: null as number | null,
  inspectionDate: '',
  inspectorName: '',
  result: 'QUALIFIED',
  totalQty: 0,
  qualifiedQty: 0,
  defectQty: 0,
  defectType: '',
  remark: '',
})
const caForm = ref({
  inspectionId: null as number | null,
  supplierId: null as number | null,
  issueDescription: '',
  rootCause: '',
  correctiveMeasures: '',
  dueDate: '',
  remark: '',
})

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadInspections() {
  if (orgId.value == null) return
  const r = await qualityApi.listInspections(orgId.value)
  inspections.value = r.data
}

async function loadCas() {
  if (orgId.value == null) return
  const r = await qualityApi.listCAs(orgId.value)
  cas.value = r.data
}

async function loadGrForOrg() {
  if (orgId.value == null) {
    grList.value = []
    return
  }
  const r = await executionApi.listGoodsReceipts(orgId.value)
  grList.value = r.data
}

watch(orgId, () => {
  loadInspections()
  loadCas()
  loadGrForOrg()
})

onMounted(async () => {
  await loadOrgs()
  const [s] = await Promise.all([masterApi.listSuppliers()])
  suppliers.value = s.data
  await loadGrForOrg()
  await loadInspections()
  await loadCas()
})

function openInsp() {
  inspForm.value = {
    grId: grList.value[0]?.id ?? null,
    inspectionDate: new Date().toISOString().slice(0, 10),
    inspectorName: '',
    result: 'QUALIFIED',
    totalQty: 0,
    qualifiedQty: 0,
    defectQty: 0,
    defectType: '',
    remark: '',
  }
  inspDialog.value = true
}

async function saveInsp() {
  if (inspForm.value.grId == null) {
    ElMessage.warning('请选择收货单')
    return
  }
  try {
    await qualityApi.createInspection({
      grId: inspForm.value.grId,
      inspectionDate: inspForm.value.inspectionDate,
      inspectorName: inspForm.value.inspectorName || undefined,
      result: inspForm.value.result,
      totalQty: Number(inspForm.value.totalQty),
      qualifiedQty: Number(inspForm.value.qualifiedQty),
      defectQty: Number(inspForm.value.defectQty),
      defectType: inspForm.value.defectType || undefined,
      remark: inspForm.value.remark || undefined,
    })
    ElMessage.success('已保存')
    inspDialog.value = false
    await loadInspections()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '保存失败')
  }
}

function openCa() {
  caForm.value = {
    inspectionId: inspections.value[0]?.id ?? null,
    supplierId: suppliers.value[0]?.id ?? null,
    issueDescription: '',
    rootCause: '',
    correctiveMeasures: '',
    dueDate: '',
    remark: '',
  }
  caDialog.value = true
}

async function saveCa() {
  if (orgId.value == null) {
    ElMessage.warning('请选择采购组织')
    return
  }
  if (caForm.value.supplierId == null) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!caForm.value.issueDescription.trim()) {
    ElMessage.warning('请填写问题描述')
    return
  }
  try {
    await qualityApi.createCA({
      inspectionId: caForm.value.inspectionId ?? undefined,
      supplierId: caForm.value.supplierId,
      procurementOrgId: orgId.value,
      issueDescription: caForm.value.issueDescription,
      rootCause: caForm.value.rootCause || undefined,
      correctiveMeasures: caForm.value.correctiveMeasures || undefined,
      dueDate: caForm.value.dueDate || undefined,
      remark: caForm.value.remark || undefined,
    })
    ElMessage.success('已保存')
    caDialog.value = false
    await loadCas()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '保存失败')
  }
}

async function closeCa(row: CorrectiveActionItem) {
  try {
    await qualityApi.closeCA(row.id)
    ElMessage.success('已关闭')
    await loadCas()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">质量协同</span>
      <el-select v-model="orgId" placeholder="采购组织" class="org-select">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="质检记录" name="inspections">
        <div class="tab-actions">
          <el-button type="primary" @click="openInsp">登记质检</el-button>
        </div>
        <el-table :data="inspections" stripe>
          <template #empty>
            <DataTableEmpty />
          </template>
          <el-table-column prop="inspectionNo" label="质检单号" width="140" />
          <el-table-column prop="grNo" label="收货单" width="130" />
          <el-table-column label="供应商" min-width="160">
            <template #default="{ row }">{{ row.supplierCode }} {{ row.supplierName }}</template>
          </el-table-column>
          <el-table-column prop="inspectionDate" label="检验日" width="120" />
          <el-table-column prop="result" label="结果" width="100" />
          <el-table-column prop="totalQty" label="总数" width="90" />
          <el-table-column prop="qualifiedQty" label="合格" width="90" />
          <el-table-column prop="defectQty" label="不良" width="90" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="纠正措施" name="cas">
        <div class="tab-actions">
          <el-button type="primary" @click="openCa">新建整改</el-button>
        </div>
        <el-table :data="cas" stripe>
          <template #empty>
            <DataTableEmpty />
          </template>
          <el-table-column prop="caNo" label="编号" width="140" />
          <el-table-column prop="inspectionNo" label="关联质检" width="130" />
          <el-table-column label="供应商" min-width="160">
            <template #default="{ row }">{{ row.supplierCode }} {{ row.supplierName }}</template>
          </el-table-column>
          <el-table-column prop="issueDescription" label="问题描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="dueDate" label="截止" width="120" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'CLOSED'"
                link
                type="primary"
                @click="closeCa(row)"
              >
                关闭
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="inspDialog" title="登记质检" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="收货单">
          <el-select v-model="inspForm.grId" placeholder="选择 GR" filterable style="width: 100%">
            <el-option v-for="g in grList" :key="g.id" :label="`${g.grNo} · ${g.poNo}`" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验日">
          <el-date-picker v-model="inspForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="检验员">
          <el-input v-model="inspForm.inspectorName" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="inspForm.result" style="width: 100%">
            <el-option label="合格" value="QUALIFIED" />
            <el-option label="不合格" value="UNQUALIFIED" />
            <el-option label="让步接收" value="CONCESSION" />
          </el-select>
        </el-form-item>
        <el-form-item label="总数量">
          <el-input-number v-model="inspForm.totalQty" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="合格数">
          <el-input-number v-model="inspForm.qualifiedQty" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良数">
          <el-input-number v-model="inspForm.defectQty" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良类型">
          <el-input v-model="inspForm.defectType" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inspForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspDialog = false">取消</el-button>
        <el-button type="primary" @click="saveInsp">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="caDialog" title="新建纠正措施" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="关联质检">
          <el-select v-model="caForm.inspectionId" clearable placeholder="可选" style="width: 100%">
            <el-option
              v-for="q in inspections"
              :key="q.id"
              :label="q.inspectionNo"
              :value="q.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="caForm.supplierId" filterable placeholder="选择" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input v-model="caForm.issueDescription" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="根因">
          <el-input v-model="caForm.rootCause" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="纠正措施">
          <el-input v-model="caForm.correctiveMeasures" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="caForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="caForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCa">保存</el-button>
      </template>
    </el-dialog>
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
.org-select {
  width: 240px;
  margin-left: auto;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.tab-actions {
  margin-bottom: 12px;
}
</style>
