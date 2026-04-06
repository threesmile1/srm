<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, type UserItem } from '../../api/auth'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { masterApi, type Supplier } from '../../api/master'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rows = ref<UserItem[]>([])
const orgOptions = ref<OrgUnit[]>([])
const supplierOptions = ref<Supplier[]>([])
const dialog = ref(false)
const editing = ref<UserItem | null>(null)
const form = ref({
  username: '',
  password: '',
  displayName: '',
  enabled: true,
  defaultProcurementOrgId: null as number | null,
  supplierId: null as number | null,
  roleCodes: [] as string[],
})

const roleOptions = [
  { code: 'ADMIN', label: '系统管理员' },
  { code: 'BUYER', label: '采购员' },
  { code: 'BUYER_MANAGER', label: '采购主管' },
  { code: 'WAREHOUSE', label: '仓管员' },
  { code: 'SUPPLIER', label: '供应商用户' },
]

async function load() {
  const [u, ledgers, suppliers] = await Promise.all([
    userApi.list(),
    foundationApi.listLedgers(),
    masterApi.listSuppliers(),
  ])
  rows.value = u.data
  supplierOptions.value = suppliers.data
  if (ledgers.data.length) {
    const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
    orgOptions.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
  }
}

function openCreate() {
  editing.value = null
  form.value = {
    username: '',
    password: '',
    displayName: '',
    enabled: true,
    defaultProcurementOrgId: orgOptions.value[0]?.id ?? null,
    supplierId: null,
    roleCodes: ['BUYER'],
  }
  dialog.value = true
}

function openEdit(row: UserItem) {
  editing.value = row
  form.value = {
    username: row.username,
    password: '',
    displayName: row.displayName || '',
    enabled: row.enabled,
    defaultProcurementOrgId: row.defaultProcurementOrgId,
    supplierId: row.supplierId,
    roleCodes: [...row.roleCodes],
  }
  dialog.value = true
}

async function save() {
  try {
    if (editing.value) {
      await userApi.update(editing.value.id, {
        displayName: form.value.displayName || undefined,
        enabled: form.value.enabled,
        defaultProcurementOrgId: form.value.defaultProcurementOrgId,
        supplierId: form.value.supplierId,
        roleCodes: form.value.roleCodes,
      })
      ElMessage.success('已保存')
    } else {
      if (!form.value.username || !form.value.password) {
        ElMessage.warning('用户名和密码必填')
        return
      }
      await userApi.create({
        username: form.value.username,
        password: form.value.password,
        displayName: form.value.displayName || undefined,
        defaultProcurementOrgId: form.value.defaultProcurementOrgId,
        supplierId: form.value.supplierId,
        roleCodes: form.value.roleCodes,
      })
      ElMessage.success('已创建')
    }
    dialog.value = false
    await load()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '操作失败')
  }
}

function roleLabel(code: string) {
  return roleOptions.find((r) => r.code === code)?.label ?? code
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">用户管理</span>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="displayName" label="显示名" width="160" />
      <el-table-column label="角色">
        <template #default="{ row }">
          <el-tag v-for="r in row.roleCodes" :key="r" size="small" style="margin-right:4px">{{ roleLabel(r) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑用户' : '新建用户'" width="520px">
      <el-form label-width="120px">
        <el-form-item v-if="!editing" label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="form.roleCodes">
            <el-checkbox v-for="r in roleOptions" :key="r.code" :value="r.code">{{ r.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="默认采购组织">
          <el-select v-model="form.defaultProcurementOrgId" clearable placeholder="选择" style="width:100%">
            <el-option v-for="o in orgOptions" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联供应商">
          <el-select v-model="form.supplierId" clearable filterable placeholder="内部用户留空" style="width:100%">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
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
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
</style>
