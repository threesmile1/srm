<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload as UploadIcon } from '@element-plus/icons-vue'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { purchaseApi, type PoSummary, type PoImportResult } from '../../api/purchase'
import { executionApi, downloadArrayBuffer } from '../../api/execution'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'po-list')
const rows = ref<PoSummary[]>([])
const tableRef = ref()

const importDialogVisible = ref(false)
const importResult = ref<PoImportResult | null>(null)
const importing = ref(false)
const u9PoSyncing = ref(false)

const statusMap: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审核',
  RELEASED: '已发布',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

async function loadOrgs() {
  const ledgers = await foundationApi.listLedgers()
  if (!ledgers.data.length) return
  const ou = await foundationApi.listOrgUnits(ledgers.data[0].id)
  orgs.value = ou.data.filter((o) => o.orgType === 'PROCUREMENT')
}

async function loadPos() {
  if (orgId.value == null) return
  const r = await purchaseApi.list(orgId.value)
  rows.value = r.data
}

watch(orgId, () => {
  loadPos()
})

onMounted(async () => {
  await loadOrgs()
  await loadPos()
})

function goDetail(id: number) {
  router.push(`/purchase/orders/${id}`)
}

async function exportSelected() {
  const sel: PoSummary[] = tableRef.value?.getSelectionRows?.() ?? []
  if (!sel.length) {
    ElMessage.warning('请选择要导出的订单')
    return
  }
  try {
    const r = await executionApi.exportPurchaseOrders(sel.map((x) => x.id))
    downloadArrayBuffer(r.data, 'srm-purchase-orders.xlsx')
    ElMessage.success('已下载')
    await loadPos()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || '导出失败')
  }
}

async function syncPurchaseOrdersFromU9() {
  u9PoSyncing.value = true
  try {
    const r = await purchaseApi.syncFromU9()
    const errN = r.data.errors?.length ?? 0
    const dropped = r.data.droppedUnmappedRows ?? 0
    if (errN === 0 && dropped === 0) {
      ElMessage.success(
        `U9 采购订单同步完成：新建 ${r.data.ordersCreated}，更新 ${r.data.ordersUpdated}，帆软行 ${r.data.rowCount}`,
      )
    } else {
      const parts: string[] = []
      parts.push(`新建 ${r.data.ordersCreated}，更新 ${r.data.ordersUpdated}`)
      parts.push(`帆软行 ${r.data.rowCount}，归组 ${r.data.groupsTotal ?? '—'}`)
      if (dropped > 0) {
        parts.push(`未归组 ${dropped} 行（缺单据编号或核算组织列）`)
      }
      if (errN > 0) {
        parts.push(`失败/跳过 ${errN} 单`)
      }
      ElMessage.warning(parts.join('；'))
      const counts = r.data.errorReasonCounts ?? {}
      const countLines = Object.entries(counts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 12)
        .map(([k, v]) => `${v}× ${k}`)
      const samples = (r.data.errors ?? []).slice(0, 25).join('\n')
      const detailBlocks: string[] = []
      if (dropped > 0) {
        detailBlocks.push(
          `有 ${dropped} 行帆软数据未参与归组：缺少「单据编号」或「核算组织」列值，或列名与模板不一致（后端已支持列名大小写不敏感）。`,
        )
      }
      if (countLines.length) {
        detailBlocks.push('【原因聚合】\n' + countLines.join('\n'))
      }
      if (errN > 0) {
        detailBlocks.push('【样例明细（前 25 条）】\n' + (samples || '（无）'))
      }
      if (detailBlocks.length) {
        await ElMessageBox.alert(detailBlocks.join('\n\n'), 'U9 采购订单同步结果', {
          confirmButtonText: '关闭',
          customClass: 'u9-po-sync-detail-box',
        })
      }
    }
    await loadPos()
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.error(msg || 'U9 采购订单同步失败')
  } finally {
    u9PoSyncing.value = false
  }
}

async function handleImport(uploadFile: { raw: File }) {
  importing.value = true
  importResult.value = null
  try {
    const r = await purchaseApi.importOrders(uploadFile.raw)
    importResult.value = r.data
    if (r.data.errors.length === 0) {
      ElMessage.success(`导入完成：创建 ${r.data.ordersCreated} 个订单，${r.data.linesCreated} 行`)
      importDialogVisible.value = false
    }
    await loadPos()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">采购订单</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="$router.push('/purchase/orders/new')">新建</el-button>
      <el-button :icon="UploadIcon" @click="importDialogVisible = true; importResult = null">Excel 导入</el-button>
      <el-button type="success" plain :loading="u9PoSyncing" @click="syncPurchaseOrdersFromU9">
        从 U9 同步采购订单（帆软）
      </el-button>
      <el-button @click="exportSelected">导出选中（U9）</el-button>
    </div>
    <el-table ref="tableRef" :data="rows" stripe @row-dblclick="(row: PoSummary) => goDetail(row.id)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column type="selection" width="42" />
      <el-table-column prop="poNo" label="订单号" width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="supplierCode" label="供应商" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="exportStatus" label="导出" width="100" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="importDialogVisible" title="Excel 导入采购订单" width="620px">
      <div class="import-hint">
        <p>Excel 模板列顺序：<b>采购组织编码 | 供应商编码 | 币种 | 备注 | 物料编码 | 仓库编码 | 数量 | 单价 | 交期(yyyy-MM-dd)</b></p>
        <p>第一行为表头（将跳过）。相同(组织+供应商+币种+备注)的连续行将合并为同一个订单。</p>
      </div>
      <el-upload
        drag
        :auto-upload="false"
        accept=".xlsx,.xls"
        :limit="1"
        :on-change="handleImport"
        :disabled="importing"
        :show-file-list="false"
      >
        <div style="padding:20px 0">
          <el-icon style="font-size:40px;color:var(--el-color-primary)"><UploadIcon /></el-icon>
          <div style="margin-top:8px">{{ importing ? '导入中...' : '点击或拖拽上传 Excel 文件' }}</div>
        </div>
      </el-upload>
      <div v-if="importResult" class="import-result">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="总行数">{{ importResult.totalRows }}</el-descriptions-item>
          <el-descriptions-item label="创建订单">{{ importResult.ordersCreated }}</el-descriptions-item>
          <el-descriptions-item label="创建行数">{{ importResult.linesCreated }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="importResult.errors.length" class="import-errors">
          <p style="font-weight:600;color:var(--el-color-danger);margin:12px 0 4px">错误明细：</p>
          <ul>
            <li v-for="(err, i) in importResult.errors" :key="i">{{ err }}</li>
          </ul>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.import-hint {
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  line-height: 1.6;
}
.import-result {
  margin-top: 16px;
}
.import-errors ul {
  max-height: 200px;
  overflow-y: auto;
  padding-left: 20px;
  font-size: 13px;
  color: var(--el-color-danger);
}
</style>

<style>
.u9-po-sync-detail-box {
  max-width: 720px;
}
.u9-po-sync-detail-box .el-message-box__message {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.5;
  max-height: 460px;
  overflow-y: auto;
  text-align: left;
}
</style>
