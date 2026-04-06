<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { auditApi, type AuditLogItem } from '../../api/auth'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rows = ref<AuditLogItem[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = ref(50)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await auditApi.list({ page: page.value, size: pageSize.value })
    rows.value = r.data.content
    total.value = r.data.totalElements
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p - 1
  load()
}

function formatTime(s: string | null) {
  if (!s) return ''
  try {
    return new Date(s).toLocaleString('zh-CN')
  } catch {
    return s
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">审计日志</span>
      <el-button @click="load" :loading="loading">刷新</el-button>
    </div>
    <el-table :data="rows" stripe v-loading="loading">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="action" label="操作" width="140" />
      <el-table-column prop="entityType" label="对象类型" width="120" />
      <el-table-column prop="entityId" label="对象ID" width="80" />
      <el-table-column prop="detail" label="详情" show-overflow-tooltip />
      <el-table-column prop="ipAddress" label="IP" width="130" />
    </el-table>
    <div style="margin-top:16px;display:flex;justify-content:flex-end">
      <el-pagination
        :current-page="page + 1"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
</style>
