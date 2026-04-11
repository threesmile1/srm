<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalApi, type AsnNotice } from '../api/portal'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const rows = ref<AsnNotice[]>([])

const statusLabel: Record<string, string> = {
  SUBMITTED: '已提交',
  CANCELLED: '已作废',
}

async function load() {
  const r = await portalApi.listAsn()
  rows.value = r.data
}

onMounted(() => {
  load()
})

async function voidNotice(row: AsnNotice) {
  if (row.status !== 'SUBMITTED') return
  try {
    await ElMessageBox.confirm(
      `确定作废发货通知 ${row.asnNo}？作废后将释放可发货占用，可重新创建发货通知。`,
      '作废确认',
      { type: 'warning', confirmButtonText: '作废', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await portalApi.voidAsn(row.id)
    ElMessage.success('已作废')
    await load()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '作废失败')
  }
}
</script>

<template>
  <div class="page">
    <h2 class="title">我的发货通知</h2>
    <p class="hint">
      <router-link to="/asn/new">新建 ASN</router-link>
      <span class="sep">·</span>
      数据范围与登录时供应商编号一致
    </p>
    <el-table :data="rows" stripe style="margin-top: 16px">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="asnNo" label="ASN 单号" width="160" />
      <el-table-column prop="poNo" label="采购订单" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ statusLabel[row.status] ?? row.status }}
        </template>
      </el-table-column>
      <el-table-column prop="shipDate" label="发货日" width="120" />
      <el-table-column prop="etaDate" label="预计到货" width="120" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'SUBMITTED'"
            link
            type="danger"
            @click="voidNotice(row)"
          >
            作废
          </el-button>
          <span v-else class="op-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.lines" size="small">
            <el-table-column prop="poLineNo" label="订单行" width="80" />
            <el-table-column prop="materialCode" label="物料" width="120" />
            <el-table-column prop="materialName" label="名称" />
            <el-table-column prop="shipQty" label="发货量" width="100" />
          </el-table>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page {
  max-width: none;
  margin: 0;
  padding: 0;
}
.sep {
  margin: 0 6px;
  color: #d1d5db;
}
.title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}
.hint a {
  color: var(--el-color-primary);
}
.op-muted {
  color: var(--el-text-color-placeholder);
}
</style>
