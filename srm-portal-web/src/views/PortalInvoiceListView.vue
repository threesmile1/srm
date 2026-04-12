<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalInvoiceApi, portalInvoiceAttachmentDownloadUrl, type InvoiceSummary } from '../api/invoice'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const route = useRoute()
const rows = ref<InvoiceSummary[]>([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<any>(null)

const statusMap: Record<string, string> = {
  SUBMITTED: '已提交', CONFIRMED: '已确认', REJECTED: '已退回', CANCELLED: '已取消',
}

const kindMap: Record<string, string> = {
  ORDINARY_VAT: '普票',
  SPECIAL_VAT: '专票',
}

async function load() {
  try {
    rows.value = (await portalInvoiceApi.list()).data
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '加载发票列表失败，请确认已用供应商账号登录且会话未过期')
  }
}

onMounted(load)
watch(
  () => route.path,
  (p) => {
    if (p === '/invoices') load()
  },
)

async function showDetail(row: InvoiceSummary) {
  detailVisible.value = true
  currentDetail.value = null
  detailLoading.value = true
  try {
    currentDetail.value = (await portalInvoiceApi.get(row.id)).data
  } catch (e: unknown) {
    detailVisible.value = false
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '加载发票详情失败')
  } finally {
    detailLoading.value = false
  }
}

function onDetailClosed() {
  currentDetail.value = null
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">我的发票</span>
      <el-button type="primary" @click="$router.push('/invoices/new')">提交发票</el-button>
    </div>
    <el-table :data="rows" stripe @row-dblclick="showDetail">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="invoiceNo" label="发票号" width="180" />
      <el-table-column label="票种" width="72">
        <template #default="{ row }">{{ kindMap[row.invoiceKind] || row.invoiceKind }}</template>
      </el-table-column>
      <el-table-column prop="invoiceDate" label="开票日期" width="110" />
      <el-table-column prop="totalAmount" label="金额" width="120" />
      <el-table-column prop="taxAmount" label="税额" width="100" />
      <el-table-column prop="currency" label="币种" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">
            {{ statusMap[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 勿在 el-dialog 上使用 v-if="currentDetail"：与 v-model 同用会导致首次无法弹出 -->
    <el-dialog
      v-model="detailVisible"
      title="发票详情"
      width="800px"
      append-to-body
      destroy-on-close
      @closed="onDetailClosed"
    >
      <div v-loading="detailLoading" class="detail-wrap">
        <template v-if="currentDetail">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="发票号">{{ currentDetail.invoiceNo }}</el-descriptions-item>
            <el-descriptions-item label="开票日期">{{ currentDetail.invoiceDate }}</el-descriptions-item>
            <el-descriptions-item label="票种">{{ kindMap[currentDetail.invoiceKind] || currentDetail.invoiceKind }}</el-descriptions-item>
            <el-descriptions-item label="税务发票代码">{{ currentDetail.vatInvoiceCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="税务发票号码">{{ currentDetail.vatInvoiceNumber || '—' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusMap[currentDetail.status] || currentDetail.status }}</el-descriptions-item>
            <el-descriptions-item label="总金额">{{ currentDetail.totalAmount }}</el-descriptions-item>
            <el-descriptions-item label="税额">{{ currentDetail.taxAmount }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ currentDetail.remark }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="currentDetail.attachments?.length" class="att-block">
            <div class="att-title">发票附件</div>
            <ul class="att-list">
              <li v-for="a in currentDetail.attachments" :key="a.id">
                <a
                  :href="portalInvoiceAttachmentDownloadUrl(currentDetail.id, a.id)"
                  target="_blank"
                  rel="noopener"
                >{{ a.originalName }}</a>
                <span class="att-meta">{{ (a.fileSize / 1024).toFixed(1) }} KB</span>
              </li>
            </ul>
          </div>
          <el-table :data="currentDetail.lines" border size="small" style="margin-top: 12px">
            <el-table-column prop="lineNo" label="#" width="50" />
            <el-table-column prop="materialCode" label="物料编码" width="120" />
            <el-table-column prop="materialName" label="物料名称" />
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column prop="unitPrice" label="单价" width="100" />
            <el-table-column prop="amount" label="金额" width="120" />
            <el-table-column prop="poNo" label="关联PO" width="180" />
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
.att-block { margin-top: 12px; }
.att-title { font-weight: 600; margin-bottom: 8px; font-size: 14px; }
.att-list { margin: 0; padding-left: 18px; }
.att-meta { margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.detail-wrap { min-height: 120px; }
</style>
