<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/http'

const ping = ref('')
const ledgers = ref<{ id: number; code: string; name: string; u9LedgerCode: string | null }[]>([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const p = await api.get('/api/v1/public/ping', { responseType: 'text' })
    ping.value = (p.data as string)?.trim() || ''
    const l = await api.get('/api/v1/ledgers')
    ledgers.value = l.data
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '请求失败（请确认后端已启动且 MySQL 已就绪）'
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-page-header content="一期 · 基础平台联调" />
    <el-alert v-if="error" :title="error" type="error" show-icon style="margin-top: 16px" />
    <p class="nav-hint">请使用左侧菜单进入 <strong>供应商 / 物料 / 采购订单</strong>。</p>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>后端连通</template>
          <p><code>/api/v1/public/ping</code> → <strong>{{ ping || '—' }}</strong></p>
          <el-button type="primary" @click="load">重新检测</el-button>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>账套列表</template>
          <el-table v-if="ledgers.length" :data="ledgers" size="small" stripe>
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="u9LedgerCode" label="U9 账套编码" width="140" />
          </el-table>
          <p v-else class="muted">暂无数据或未连上后端</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}
.muted {
  color: var(--el-text-color-secondary);
}
.nav-hint {
  margin-top: 12px;
  color: var(--el-text-color-regular);
}
</style>
