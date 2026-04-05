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
  <div class="page srm-page srm-page--fluid">
    <div class="page-head">
      <h2 class="page-title">工作台</h2>
      <p class="page-desc">基础平台连通性与账套一览</p>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon style="margin-top: 16px" />
    <p class="nav-hint">请使用左侧菜单进入 <strong>主数据</strong>与 <strong>采购执行</strong>模块。</p>
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
  padding: 0;
}
.page-head {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}
.page-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
}
.muted {
  color: var(--el-text-color-secondary);
}
.nav-hint {
  margin-top: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
