<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { portalInvoiceApi, type InvoiceSummary } from '../api/invoice'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const rows = ref<InvoiceSummary[]>([])
const detailVisible = ref(false)
const currentDetail = ref<any>(null)

const statusMap: Record<string, string> = {
  SUBMITTED: '已提交', CONFIRMED: '已确认', REJECTED: '已退回', CANCELLED: '已取消',
}

async function load() {
  rows.value = (await portalInvoiceApi.list()).data
}

onMounted(load)

async function showDetail(row: InvoiceSummary) {
  currentDetail.value = (await portalInvoiceApi.get(row.id)).data
  detailVisible.value = true
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
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="发票详情" width="800px" v-if="currentDetail">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="发票号">{{ currentDetail.invoiceNo }}</el-descriptions-item>
        <el-descriptions-item label="开票日期">{{ currentDetail.invoiceDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusMap[currentDetail.status] || currentDetail.status }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ currentDetail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ currentDetail.taxAmount }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ currentDetail.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="currentDetail.lines" border size="small" style="margin-top: 12px">
        <el-table-column prop="lineNo" label="#" width="50" />
        <el-table-column prop="materialCode" label="物料编码" width="120" />
        <el-table-column prop="materialName" label="物料名称" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column prop="poNo" label="关联PO" width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 600; }
</style>
