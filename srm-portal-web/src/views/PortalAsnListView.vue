<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { CircleClose, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalApi, type AsnNotice } from '../api/portal'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const rows = ref<AsnNotice[]>([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailRow = ref<AsnNotice | null>(null)
const apiBase = (import.meta.env.VITE_API_BASE as string) || 'http://localhost:8080'

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

async function openDetail(row: AsnNotice) {
  detailOpen.value = true
  detailLoading.value = true
  detailRow.value = null
  try {
    detailRow.value = (await portalApi.getAsn(row.id)).data
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '加载详情失败')
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

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

function downloadLogisticsAttachment(row: AsnNotice) {
  window.open(`${apiBase}/api/v1/portal/asn-notices/${row.id}/logistics-attachment/file`, '_blank')
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
      <el-table-column type="expand" width="48">
        <template #default="{ row }">
          <el-table :data="row.lines" size="small">
            <el-table-column prop="poLineNo" label="订单行" width="80" />
            <el-table-column prop="materialCode" label="物料" width="120" />
            <el-table-column prop="materialName" label="名称" />
            <el-table-column prop="shipQty" label="发货量" width="100" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="asnNo" label="ASN 单号" width="160" min-width="140" />
      <el-table-column prop="poNo" label="采购订单" width="160" min-width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ statusLabel[row.status] ?? row.status }}
        </template>
      </el-table-column>
      <el-table-column prop="shipDate" label="发货日" width="120" />
      <el-table-column prop="etaDate" label="预计到货" width="120" />
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <div class="op-cell">
            <el-button type="primary" link size="small" :icon="Document" @click="openDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              class="void-btn"
              type="danger"
              plain
              size="small"
              :icon="CircleClose"
              @click="voidNotice(row)"
            >
              作废
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailOpen" title="发货通知详情" size="520px" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detailRow">
        <el-descriptions :column="1" border size="small" class="detail-desc">
          <el-descriptions-item label="ASN 单号">{{ detailRow.asnNo }}</el-descriptions-item>
          <el-descriptions-item label="采购订单">{{ detailRow.poNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel[detailRow.status] ?? detailRow.status }}</el-descriptions-item>
          <el-descriptions-item label="发货日">{{ detailRow.shipDate }}</el-descriptions-item>
          <el-descriptions-item label="预计到货">{{ detailRow.etaDate ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="承运商">{{ detailRow.carrier ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="运单号">{{ detailRow.trackingNo ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ detailRow.receiverName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ detailRow.receiverPhone ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ detailRow.receiverAddress ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="物流单附件">
            <template v-if="detailRow.logisticsAttachmentOriginalName">
              <el-button type="primary" link size="small" @click="downloadLogisticsAttachment(detailRow)">
                下载（{{ detailRow.logisticsAttachmentOriginalName }}）
              </el-button>
            </template>
            <template v-else>—</template>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailRow.remark ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-lines-title">发货明细</div>
        <el-table :data="detailRow.lines" size="small" stripe border style="width: 100%">
          <el-table-column prop="poLineNo" label="订单行" width="72" />
          <el-table-column prop="materialCode" label="物料" width="110" />
          <el-table-column prop="materialName" label="名称" min-width="120" />
          <el-table-column prop="shipQty" label="发货量" width="90" />
        </el-table>
      </template>
    </el-drawer>
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

.op-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}

.detail-desc {
  margin-bottom: 12px;
}

.detail-lines-title {
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--el-text-color-regular);
}

/* 作废：浅色描边 + 悬停略加深，比纯文字链接更易识别、易点击 */
.void-btn {
  padding: 5px 12px;
  font-weight: 500;
  border-radius: 6px;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}
.void-btn:hover {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
}
.void-btn:focus-visible {
  outline: 2px solid var(--el-color-danger-light-5);
  outline-offset: 1px;
}
</style>
