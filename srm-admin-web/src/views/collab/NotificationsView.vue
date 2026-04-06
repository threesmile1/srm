<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi, type NotificationItem } from '../../api/notification'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const rows = ref<NotificationItem[]>([])
const loading = ref(false)

function emitInboxUpdated() {
  window.dispatchEvent(new CustomEvent('srm-admin-inbox-updated'))
}

async function load() {
  loading.value = true
  try {
    const r = await notificationApi.list()
    rows.value = r.data
  } finally {
    loading.value = false
  }
}

async function markRead(row: NotificationItem) {
  if (row.read) return
  try {
    await notificationApi.markRead(row.id)
    row.read = true
    emitInboxUpdated()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function markAll() {
  try {
    await notificationApi.markAllRead()
    ElMessage.success('已全部标为已读')
    emitInboxUpdated()
    await load()
  } catch {
    ElMessage.error('操作失败')
  }
}

function goRef(row: NotificationItem) {
  if (row.refType === 'PO' && row.refId != null) {
    router.push(`/purchase/orders/${row.refId}`)
  } else if (row.refType === 'INVOICE' && row.refId != null) {
    router.push(`/invoice/${row.refId}`)
  } else if (row.refType === 'APPROVAL' && row.refId != null) {
    router.push('/approval/list')
  } else if (row.refType === 'GR' && row.refId != null) {
    router.push('/purchase/receipts')
  } else if (row.refType === 'PERF_EVAL' && row.refId != null) {
    router.push(`/perf/evaluations/${row.refId}`)
  } else if (row.refType === 'CONTRACT' && row.refId != null) {
    router.push(`/sourcing/contracts/${row.refId}`)
  } else if (
    (row.refType === 'QUALITY_INSPECTION' || row.refType === 'CORRECTIVE_ACTION') &&
    row.refId != null
  ) {
    router.push('/quality')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">消息中心</span>
      <div class="actions">
        <el-button @click="markAll">全部标为已读</el-button>
        <el-button type="primary" @click="load">刷新</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="rows" stripe @row-click="(r: NotificationItem) => markRead(r)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column label="" width="72">
        <template #default="{ row }">
          <el-tag v-if="!row.read" type="danger" size="small">未读</el-tag>
          <span v-else class="muted">已读</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column prop="createdAt" label="时间" width="200" />
      <el-table-column label="操作" width="132" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.refType === 'PO' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            查看订单
          </el-button>
          <el-button
            v-else-if="row.refType === 'INVOICE' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            查看发票
          </el-button>
          <el-button
            v-else-if="row.refType === 'APPROVAL' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            审批工作台
          </el-button>
          <el-button
            v-else-if="row.refType === 'GR' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            收货单列表
          </el-button>
          <el-button
            v-else-if="row.refType === 'PERF_EVAL' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            考核详情
          </el-button>
          <el-button
            v-else-if="row.refType === 'CONTRACT' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            合同详情
          </el-button>
          <el-button
            v-else-if="
              (row.refType === 'QUALITY_INSPECTION' || row.refType === 'CORRECTIVE_ACTION') &&
              row.refId != null
            "
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            质量协同
          </el-button>
        </template>
      </el-table-column>
    </el-table>
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
.actions {
  display: flex;
  gap: 8px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
