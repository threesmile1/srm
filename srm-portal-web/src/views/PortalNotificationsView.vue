<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalNotificationApi, type PortalNotificationItem } from '../api/notification'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const router = useRouter()
const rows = ref<PortalNotificationItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await portalNotificationApi.list()
    rows.value = r.data
  } finally {
    loading.value = false
  }
}

async function markRead(row: PortalNotificationItem) {
  if (row.read) return
  try {
    await portalNotificationApi.markRead(row.id)
    row.read = true
  } catch {
    ElMessage.error('操作失败')
  }
}

async function markAll() {
  try {
    await portalNotificationApi.markAllRead()
    ElMessage.success('已全部标为已读')
    await load()
  } catch {
    ElMessage.error('操作失败')
  }
}

function goRef(row: PortalNotificationItem) {
  if (row.refType === 'PO' && row.refId != null) {
    router.push(`/pos/${row.refId}`)
  } else if (row.refType === 'RFQ' && row.refId != null) {
    router.push(`/rfq/${row.refId}`)
  } else if (row.refType === 'ASN' && row.refId != null) {
    router.push('/asn')
  } else if (row.refType === 'INVOICE' && row.refId != null) {
    router.push('/invoices')
  } else if (row.refType === 'PERF_EVAL' && row.refId != null) {
    router.push(`/perf/${row.refId}`)
  } else if (row.refType === 'CONTRACT' && row.refId != null) {
    router.push({ path: '/contracts', query: { openId: String(row.refId) } })
  } else if (row.refType === 'QUALITY_INSPECTION' && row.refId != null) {
    router.push({ path: '/quality', query: { inspectionId: String(row.refId) } })
  } else if (row.refType === 'CORRECTIVE_ACTION' && row.refId != null) {
    router.push({ path: '/quality', query: { caId: String(row.refId) } })
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h2 class="title">消息中心</h2>
    <p class="hint">站内通知（订单、询价、ASN、发票、绩效发布、合同与质量整改等）。点击行可标为已读；带「操作」的可跳转查阅。</p>
    <div class="toolbar">
      <el-button @click="markAll">全部标为已读</el-button>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe @row-click="(r: PortalNotificationItem) => markRead(r)">
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
      <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="createdAt" label="时间" width="200" />
      <el-table-column label="操作" width="120" fixed="right">
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
            v-else-if="row.refType === 'RFQ' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            查看询价
          </el-button>
          <el-button
            v-else-if="row.refType === 'ASN' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            发货通知
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
            查看合同
          </el-button>
          <el-button
            v-else-if="row.refType === 'QUALITY_INSPECTION' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            质检详情
          </el-button>
          <el-button
            v-else-if="row.refType === 'CORRECTIVE_ACTION' && row.refId != null"
            link
            type="primary"
            @click.stop="goRef(row)"
          >
            整改详情
          </el-button>
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
.toolbar {
  display: flex;
  gap: 8px;
  margin: 16px 0 12px;
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
