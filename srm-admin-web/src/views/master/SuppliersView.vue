<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { masterApi, type Supplier } from '../../api/master'
import { foundationApi, type OrgUnit } from '../../api/foundation'

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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">供应商</span>
      <el-button type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="rows" stripe>
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="u9VendorCode" label="U9 供应商编码" width="140" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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
