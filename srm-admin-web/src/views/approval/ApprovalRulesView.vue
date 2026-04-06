<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi, type ApprovalRule } from '../../api/approval'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rules = ref<ApprovalRule[]>([])
const dialogVisible = ref(false)
const editForm = ref({
  id: undefined as number | undefined,
  docType: 'PO',
  minAmount: 0,
  maxAmount: undefined as number | undefined,
  approvalLevel: 1,
  approverRole: 'BUYER_MANAGER',
  description: '',
  enabled: true,
})

async function loadRules() {
  rules.value = (await approvalApi.listRules()).data
}

onMounted(loadRules)

function openAdd() {
  editForm.value = { id: undefined, docType: 'PO', minAmount: 0, maxAmount: undefined, approvalLevel: 1, approverRole: 'BUYER_MANAGER', description: '', enabled: true }
  dialogVisible.value = true
}

function openEdit(row: ApprovalRule) {
  editForm.value = {
    id: row.id, docType: row.docType,
    minAmount: Number(row.minAmount), maxAmount: row.maxAmount ? Number(row.maxAmount) : undefined,
    approvalLevel: row.approvalLevel, approverRole: row.approverRole,
    description: row.description || '', enabled: row.enabled,
  }
  dialogVisible.value = true
}

async function save() {
  await approvalApi.saveRule(editForm.value)
  ElMessage.success('已保存')
  dialogVisible.value = false
  await loadRules()
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除？')
  await approvalApi.deleteRule(id)
  ElMessage.success('已删除')
  await loadRules()
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">审批规则配置</span>
      <el-button type="primary" @click="openAdd">新增规则</el-button>
    </div>
    <el-table :data="rules" stripe border size="small">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="docType" label="单据类型" width="100" />
      <el-table-column prop="minAmount" label="最低金额" width="120" />
      <el-table-column label="最高金额" width="120">
        <template #default="{ row }">{{ row.maxAmount ?? '不限' }}</template>
      </el-table-column>
      <el-table-column prop="approvalLevel" label="审批级别" width="90" />
      <el-table-column prop="approverRole" label="审批角色" width="140" />
      <el-table-column prop="description" label="说明" />
      <el-table-column label="启用" width="70">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑规则' : '新增规则'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="单据类型">
          <el-select v-model="editForm.docType">
            <el-option label="采购订单(PO)" value="PO" />
            <el-option label="请购单(PR)" value="PR" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低金额">
          <el-input-number v-model="editForm.minAmount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="最高金额">
          <el-input-number v-model="editForm.maxAmount" :min="0" :precision="2" placeholder="不填为不限" />
        </el-form-item>
        <el-form-item label="审批级别">
          <el-input-number v-model="editForm.approvalLevel" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="审批角色">
          <el-select v-model="editForm.approverRole">
            <el-option label="采购主管" value="BUYER_MANAGER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
</style>
