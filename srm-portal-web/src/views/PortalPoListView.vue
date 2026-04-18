<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalApi, type PoExportRow, type PoSummary } from '../api/portal'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const router = useRouter()
const rows = ref<PoSummary[]>([])

/** 与后端 PoStatus 一致 */
const poStatusMap: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审核',
  RELEASED: '已发布',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

function poStatusLabel(s: string | undefined) {
  if (!s) return '—'
  return poStatusMap[s] ?? s
}
const exporting = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

async function loadPos() {
  try {
    const r = await portalApi.listPosPaged(currentPage.value - 1, pageSize.value)
    rows.value = r.data.content
    total.value = r.data.totalElements
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '加载失败，请刷新或重新登录')
    rows.value = []
    total.value = 0
  }
}

onMounted(loadPos)

watch([currentPage, pageSize], () => {
  loadPos()
})

function csvEscape(v: unknown) {
  const s = v === null || v === undefined ? '' : String(v)
  const needQuote = /[",\n\r]/.test(s)
  const inner = s.replace(/"/g, '""')
  return needQuote ? `"${inner}"` : inner
}

function downloadCsv(filename: string, header: string[], lines: unknown[][]) {
  const bom = '\uFEFF'
  const content = [header.map(csvEscape).join(','), ...lines.map((r) => r.map(csvEscape).join(','))].join('\r\n')
  const blob = new Blob([bom + content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

async function exportOrders() {
  if (exporting.value) return
  exporting.value = true
  try {
    const r = await portalApi.exportPoRows()
    const data: PoExportRow[] = r.data || []
    const header = [
      '业务日期',
      '正式订单号',
      '二级门店',
      '收货人名称',
      '终端电话',
      '安装地址',
      '料品名称',
      '料品规格',
      '物料编码',
      '供应商名称',
      '供应商编码',
      '单据编号',
      '单据类型',
      '料品单位',
      '订购数量',
      '最后价',
      '协价',
      '初始价',
      '要求交货日期',
      '单价',
      '金额',
    ]
    const lines = data.map((x) => [
      x.businessDate,
      x.officialOrderNo,
      x.store2,
      x.receiverName,
      x.terminalPhone,
      x.installAddress,
      x.materialName,
      x.materialSpec,
      x.materialCode,
      x.supplierName,
      x.supplierCode,
      x.docNo,
      x.docType,
      x.uom,
      x.qty,
      x.lastPrice,
      x.negotiatedPrice,
      x.initialPrice,
      x.requestedDate,
      x.unitPrice,
      x.amount,
    ])
    const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')
    downloadCsv(`已发布采购订单_${stamp}.csv`, header, lines)
    ElMessage.success(`已导出 ${data.length} 行`)
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '导出失败')
  } finally {
    exporting.value = false
  }
}

</script>

<template>
  <div class="page">
    <div class="head">
      <h2 class="title">已发布采购订单</h2>
      <el-button type="primary" :loading="exporting" @click="exportOrders">批量导出订单</el-button>
    </div>
    <p class="hint">数据范围由登录会话中的供应商身份决定，无需在请求中传递供应商编号。</p>
    <el-table :data="rows" stripe style="margin-top: 16px" @row-dblclick="(row: PoSummary) => router.push(`/pos/${row.id}`)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="poNo" label="订单号" width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ poStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" />
      <el-table-column prop="officialOrderNo" label="正式订单号" min-width="260" show-overflow-tooltip />
      <el-table-column prop="businessDate" label="业务日期" width="110" show-overflow-tooltip />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/pos/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="() => (currentPage = 1)"
      />
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: none;
  margin: 0;
  padding: 0;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
.pager-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
