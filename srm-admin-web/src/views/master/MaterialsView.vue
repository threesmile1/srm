<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { masterApi, type Material } from '../../api/master'

const rows = ref<Material[]>([])
const dialog = ref(false)
const editing = ref<Material | null>(null)
const form = ref({ code: '', name: '', uom: '', u9ItemCode: '' })

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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">物料</span>
      <el-button type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="rows" stripe>
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
