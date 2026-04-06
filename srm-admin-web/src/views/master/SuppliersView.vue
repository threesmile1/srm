<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload as UploadIcon } from '@element-plus/icons-vue'
import { masterApi, type Supplier, type ImportResult } from '../../api/master'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { supplierLifecycleApi, type SupplierAuditItem } from '../../api/supplier'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const LIFECYCLE_LABEL: Record<string, string> = {
  PENDING_REVIEW: '待审核',
  QUALIFIED: '合格',
  TEMPORARY: '临时',
  RECTIFICATION: '整改',
  BLACKLISTED: '黑名单',
  ELIMINATED: '淘汰',
}

const LIFECYCLE_OPTIONS = [
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'QUALIFIED', label: '合格' },
  { value: 'TEMPORARY', label: '临时' },
  { value: 'RECTIFICATION', label: '整改' },
  { value: 'BLACKLISTED', label: '黑名单' },
  { value: 'ELIMINATED', label: '淘汰' },
]

const rows = ref<Supplier[]>([])
const orgOptions = ref<OrgUnit[]>([])
const dialog = ref(false)
const editing = ref<Supplier | null>(null)
const form = ref({
  code: '',
  name: '',
  u9VendorCode: '',
  taxId: '',
  procurementOrgIds: [] as number[],
})

const importDialogVisible = ref(false)
const importResult = ref<ImportResult | null>(null)
const importing = ref(false)

const lifecycleDialog = ref(false)
const lifecycleRow = ref<Supplier | null>(null)
const lifecycleStatus = ref('')
const lifecycleSaving = ref(false)

const auditDrawer = ref(false)
const auditSupplier = ref<Supplier | null>(null)
const audits = ref<SupplierAuditItem[]>([])
const auditForm = ref({
  auditType: '现场审核',
  auditDate: '',
  result: '通过',
  score: null as number | null,
  auditorName: '',
  remark: '',
})

async function load() {
  const [s, ledgers] = await Promise.all([masterApi.listSuppliers(), foundationApi.listLedgers()])
  rows.value = s.data
  if (ledgers.data.length) {
    const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
    orgOptions.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
  }
}

function openCreate() {
  editing.value = null
  form.value = {
    code: '',
    name: '',
    u9VendorCode: '',
    taxId: '',
    procurementOrgIds: orgOptions.value.map((o) => o.id),
  }
  dialog.value = true
}

function openEdit(row: Supplier) {
  editing.value = row
  form.value = {
    code: row.code,
    name: row.name,
    u9VendorCode: row.u9VendorCode || '',
    taxId: row.taxId || '',
    procurementOrgIds: [...row.procurementOrgIds],
  }
  dialog.value = true
}

async function save() {
  try {
    if (editing.value) {
      await masterApi.updateSupplier(editing.value.id, {
        name: form.value.name,
        u9VendorCode: form.value.u9VendorCode || undefined,
        taxId: form.value.taxId || undefined,
        procurementOrgIds: form.value.procurementOrgIds,
      })
      ElMessage.success('已保存')
    } else {
      await masterApi.createSupplier({
        code: form.value.code,
        name: form.value.name,
        u9VendorCode: form.value.u9VendorCode || undefined,
        taxId: form.value.taxId || undefined,
        procurementOrgIds: form.value.procurementOrgIds,
      })
      ElMessage.success('已创建')
    }
    dialog.value = false
    await load()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '操作失败')
  }
}

async function handleImport(uploadFile: { raw: File }) {
  importing.value = true
  importResult.value = null
  try {
    const r = await masterApi.importSuppliers(uploadFile.raw)
    importResult.value = r.data
    if (r.data.errors.length === 0) {
      ElMessage.success(`导入完成：新增 ${r.data.created}，更新 ${r.data.updated}`)
      importDialogVisible.value = false
    }
    await load()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

function lifecycleLabel(code: string | null | undefined) {
  if (!code) return '—'
  return LIFECYCLE_LABEL[code] ?? code
}

function openLifecycle(row: Supplier) {
  lifecycleRow.value = row
  lifecycleStatus.value = row.lifecycleStatus || 'QUALIFIED'
  lifecycleSaving.value = false
  lifecycleDialog.value = true
}

async function saveLifecycle() {
  if (!lifecycleRow.value) return
  lifecycleSaving.value = true
  try {
    await supplierLifecycleApi.updateStatus(lifecycleRow.value.id, lifecycleStatus.value)
    ElMessage.success('状态已更新')
    lifecycleDialog.value = false
    lifecycleRow.value = null
    await load()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '更新失败')
  } finally {
    lifecycleSaving.value = false
  }
}

async function openAudits(row: Supplier) {
  auditSupplier.value = row
  auditDrawer.value = true
  auditForm.value = {
    auditType: '现场审核',
    auditDate: new Date().toISOString().slice(0, 10),
    result: '通过',
    score: null,
    auditorName: '',
    remark: '',
  }
  try {
    const r = await supplierLifecycleApi.listAudits(row.id)
    audits.value = r.data
  } catch {
    audits.value = []
    ElMessage.error('加载审计记录失败')
  }
}

async function addAudit() {
  if (!auditSupplier.value) return
  if (!auditForm.value.auditDate) {
    ElMessage.warning('请选择审核日期')
    return
  }
  try {
    await supplierLifecycleApi.addAudit(auditSupplier.value.id, {
      auditType: auditForm.value.auditType,
      auditDate: auditForm.value.auditDate,
      result: auditForm.value.result,
      score: auditForm.value.score ?? undefined,
      auditorName: auditForm.value.auditorName || undefined,
      remark: auditForm.value.remark || undefined,
    })
    ElMessage.success('已记录')
    const r = await supplierLifecycleApi.listAudits(auditSupplier.value.id)
    audits.value = r.data
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '保存失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">供应商</span>
      <div style="display:flex;gap:8px">
        <el-button type="primary" @click="openCreate">新建</el-button>
        <el-button :icon="UploadIcon" @click="importDialogVisible = true; importResult = null">Excel 导入</el-button>
      </div>
    </div>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="生命周期" width="110">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ lifecycleLabel(row.lifecycleStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="u9VendorCode" label="U9 供应商编码" width="140" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openLifecycle(row)">改状态</el-button>
          <el-button link type="primary" @click="openAudits(row)">审计</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑供应商' : '新建供应商'" width="520px">
      <el-form label-width="120px">
        <el-form-item v-if="!editing" label="编码">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="U9 编码">
          <el-input v-model="form.u9VendorCode" />
        </el-form-item>
        <el-form-item label="税号">
          <el-input v-model="form.taxId" />
        </el-form-item>
        <el-form-item label="授权采购组织">
          <el-select v-model="form.procurementOrgIds" multiple placeholder="选择" style="width: 100%">
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="lifecycleDialog"
      title="调整生命周期状态"
      width="440px"
      @closed="lifecycleRow = null"
    >
      <template v-if="lifecycleRow">
        <p class="lifecycle-hint">
          {{ lifecycleRow.code }} · {{ lifecycleRow.name }}<br />
          <span class="sub">待审核/黑名单/淘汰供应商不可新建采购订单或请购转单。</span>
        </p>
        <el-select v-model="lifecycleStatus" placeholder="状态" style="width: 100%">
          <el-option v-for="o in LIFECYCLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </template>
      <template #footer>
        <el-button @click="lifecycleDialog = false">取消</el-button>
        <el-button type="primary" :loading="lifecycleSaving" @click="saveLifecycle">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="auditDrawer" :title="auditSupplier ? `审计 · ${auditSupplier.code}` : '审计'" size="480px">
      <div v-if="auditSupplier" class="audit-body">
        <el-form label-width="88px" class="audit-form">
          <el-form-item label="类型">
            <el-input v-model="auditForm.auditType" />
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker
              v-model="auditForm.auditDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="结果">
            <el-input v-model="auditForm.result" />
          </el-form-item>
          <el-form-item label="得分">
            <el-input-number v-model="auditForm.score" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="审核人">
            <el-input v-model="auditForm.auditorName" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="auditForm.remark" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="addAudit">添加记录</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="audits" stripe size="small" max-height="360">
          <el-table-column prop="auditDate" label="日期" width="110" />
          <el-table-column prop="auditType" label="类型" width="100" />
          <el-table-column prop="result" label="结果" width="80" />
          <el-table-column prop="auditorName" label="审核人" width="90" />
          <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="importDialogVisible" title="Excel 导入供应商" width="560px">
      <div class="import-hint">
        <p>Excel 模板列顺序：<b>编码 | 名称 | U9供应商编码 | 税号 | 授权采购组织编码(逗号分隔)</b></p>
        <p>第一行为表头（将跳过），编码已存在则更新。</p>
      </div>
      <el-upload
        drag
        :auto-upload="false"
        accept=".xlsx,.xls"
        :limit="1"
        :on-change="handleImport"
        :disabled="importing"
        :show-file-list="false"
      >
        <div style="padding:20px 0">
          <el-icon style="font-size:40px;color:var(--el-color-primary)"><UploadIcon /></el-icon>
          <div style="margin-top:8px">{{ importing ? '导入中...' : '点击或拖拽上传 Excel 文件' }}</div>
        </div>
      </el-upload>
      <div v-if="importResult" class="import-result">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="总行数">{{ importResult.total }}</el-descriptions-item>
          <el-descriptions-item label="新增">{{ importResult.created }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ importResult.updated }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ importResult.skipped }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="importResult.errors.length" class="import-errors">
          <p style="font-weight:600;color:var(--el-color-danger);margin:12px 0 4px">错误明细：</p>
          <ul>
            <li v-for="(err, i) in importResult.errors" :key="i">{{ err }}</li>
          </ul>
        </div>
      </div>
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
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.import-hint {
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  line-height: 1.6;
}
.import-result {
  margin-top: 16px;
}
.import-errors ul {
  max-height: 200px;
  overflow-y: auto;
  padding-left: 20px;
  font-size: 13px;
  color: var(--el-color-danger);
}
.lifecycle-hint {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.5;
}
.lifecycle-hint .sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.audit-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.audit-form {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
