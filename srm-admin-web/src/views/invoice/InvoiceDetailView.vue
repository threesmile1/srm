<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { invoiceApi, invoiceAttachmentDownloadUrl, type InvoiceDetail } from '../../api/invoice'

const route = useRoute()
const detail = ref<InvoiceDetail | null>(null)

const statusMap: Record<string, string> = {
  SUBMITTED: '已提交', CONFIRMED: '已确认', REJECTED: '已退回', CANCELLED: '已取消',
}

const kindMap: Record<string, string> = {
  ORDINARY_VAT: '增值税普通发票',
  SPECIAL_VAT: '增值税专用发票',
}

async function load() {
  detail.value = (await invoiceApi.get(Number(route.params.id))).data
}

onMounted(load)

async function confirm() {
  await invoiceApi.confirm(detail.value!.id)
  ElMessage.success('已确认')
  await load()
}

async function reject() {
  await invoiceApi.reject(detail.value!.id)
  ElMessage.success('已退回')
  await load()
}
</script>

<template>
  <div class="page" v-if="detail">
    <div class="toolbar">
      <span class="title">{{ detail.invoiceNo }}</span>
      <el-tag :type="detail.status === 'CONFIRMED' ? 'success' : detail.status === 'REJECTED' ? 'danger' : 'warning'" size="default">
        {{ statusMap[detail.status] || detail.status }}
      </el-tag>
      <div style="flex: 1" />
      <el-button @click="$router.back()">返回</el-button>
      <el-button v-if="detail.status === 'SUBMITTED'" type="success" @click="confirm">确认</el-button>
      <el-button v-if="detail.status === 'SUBMITTED'" type="danger" @click="reject">退回</el-button>
    </div>
    <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
      <el-descriptions-item label="供应商">{{ detail.supplierCode }} {{ detail.supplierName }}</el-descriptions-item>
      <el-descriptions-item label="采购组织">{{ detail.procurementOrgCode }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ detail.currency }}</el-descriptions-item>
      <el-descriptions-item label="开票日期">{{ detail.invoiceDate }}</el-descriptions-item>
      <el-descriptions-item label="票种">{{ kindMap[detail.invoiceKind] || detail.invoiceKind }}</el-descriptions-item>
      <el-descriptions-item label="税务发票代码">{{ detail.vatInvoiceCode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="税务发票号码">{{ detail.vatInvoiceNumber || '—' }}</el-descriptions-item>
      <el-descriptions-item label="总金额">{{ detail.totalAmount }}</el-descriptions-item>
      <el-descriptions-item label="税额">
        {{ detail.taxAmount }}
        <span class="sub">（明细填写税率时由行金额×税率%汇总）</span>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="3">{{ detail.remark }}</el-descriptions-item>
    </el-descriptions>
    <div v-if="(detail.attachments ?? []).length" class="att-block">
      <div class="att-title">供应商上传的发票附件</div>
      <ul class="att-list">
        <li v-for="a in detail.attachments" :key="a.id">
          <a :href="invoiceAttachmentDownloadUrl(detail.id, a.id)" target="_blank" rel="noopener">{{ a.originalName }}</a>
          <span class="att-meta">{{ (a.fileSize / 1024).toFixed(1) }} KB</span>
        </li>
      </ul>
    </div>
    <el-table :data="detail.lines" border size="small">
      <el-table-column prop="lineNo" label="#" width="50" />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" />
      <el-table-column prop="qty" label="数量" width="90" />
      <el-table-column prop="unitPrice" label="单价" width="100" />
      <el-table-column prop="amount" label="金额" width="120" />
      <el-table-column prop="taxRate" label="税率(%)" width="90" />
      <el-table-column prop="poNo" label="关联PO" width="180" />
    </el-table>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
.sub { margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary); font-weight: normal; }
.att-block { margin-bottom: 16px; }
.att-title { font-weight: 600; margin-bottom: 8px; font-size: 14px; }
.att-list { margin: 0; padding-left: 18px; }
.att-meta { margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
</style>
