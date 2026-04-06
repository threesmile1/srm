<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload as UploadIcon } from '@element-plus/icons-vue'
import { masterApi, type Material, type ImportResult } from '../../api/master'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rows = ref<Material[]>([])
const dialog = ref(false)
const editing = ref<Material | null>(null)
const form = ref({ code: '', name: '', uom: '', u9ItemCode: '' })

const importDialogVisible = ref(false)
const importResult = ref<ImportResult | null>(null)
const importing = ref(false)

async function load() {
  const r = await masterApi.listMaterials()
  rows.value = r.data
}

function openCreate() {
  editing.value = null
  form.value = { code: '', name: '', uom: 'PCS', u9ItemCode: '' }
  dialog.value = true
}

function openEdit(row: Material) {
  editing.value = row
  form.value = { code: row.code, name: row.name, uom: row.uom, u9ItemCode: row.u9ItemCode || '' }
  dialog.value = true
}

async function save() {
  try {
    if (editing.value) {
      await masterApi.updateMaterial(editing.value.id, {
        name: form.value.name,
        uom: form.value.uom,
        u9ItemCode: form.value.u9ItemCode || undefined,
      })
    } else {
      await masterApi.createMaterial({
        code: form.value.code,
        name: form.value.name,
        uom: form.value.uom,
        u9ItemCode: form.value.u9ItemCode || undefined,
      })
    }
    ElMessage.success('已保存')
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
    const r = await masterApi.importMaterials(uploadFile.raw)
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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">物料</span>
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
      <el-table-column prop="uom" label="单位" width="80" />
      <el-table-column prop="u9ItemCode" label="U9 料号" width="120" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑物料' : '新建物料'" width="480px">
      <el-form label-width="100px">
        <el-form-item v-if="!editing" label="编码">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.uom" />
        </el-form-item>
        <el-form-item label="U9 料号">
          <el-input v-model="form.u9ItemCode" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="importDialogVisible" title="Excel 导入物料" width="560px">
      <div class="import-hint">
        <p>Excel 模板列顺序：<b>编码 | 名称 | 单位 | U9料号</b></p>
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
</style>
