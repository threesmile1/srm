<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import {
  masterApi,
  type Material,
  type U9MaterialSyncResult,
  type FactoryWarehouseSyncResult,
  type U9LpgysBulkSyncResult,
} from '../../api/master'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rows = ref<Material[]>([])
/** 表格分页：与 el-pagination 一致为 1 基；请求 API 时减 1 */
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialog = ref(false)
const editing = ref<Material | null>(null)
const form = ref({ name: '', uom: '', u9ItemCode: '' })

const u9Syncing = ref(false)
const u9SyncResult = ref<U9MaterialSyncResult | null>(null)
const u9SyncDialogVisible = ref(false)

const factoryWhSyncing = ref(false)
const factoryWhResult = ref<FactoryWarehouseSyncResult | null>(null)
const factoryWhDialogVisible = ref(false)
const lpgysBulkSyncing = ref(false)
const lpgysBulkResult = ref<U9LpgysBulkSyncResult | null>(null)
const lpgysBulkDialogVisible = ref(false)

async function load() {
  try {
    const r = await masterApi.listMaterials({
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    rows.value = r.data.content
    total.value = r.data.totalElements
  } catch (e: unknown) {
    rows.value = []
    total.value = 0
    let msg = '加载物料列表失败'
    if (axios.isAxiosError(e)) {
      const st = e.response?.status
      if (st === 401) {
        msg = '未登录或会话已过期，请先登录后再打开物料页'
      } else if (e.code === 'ERR_NETWORK' || e.message === 'Network Error') {
        msg =
          '网络错误：请确认后端已启动（默认 8080），并使用 npm run dev（走 /api 代理）；勿用 file:// 打开页面。'
      } else {
        msg = (e.response?.data as { error?: string } | undefined)?.error ?? e.message ?? msg
      }
    }
    ElMessage.error(msg)
  }
}

function onPageChange() {
  load()
}

function onSizeChange() {
  currentPage.value = 1
  load()
}

function openEdit(row: Material) {
  editing.value = row
  form.value = { name: row.name, uom: row.uom, u9ItemCode: row.u9ItemCode || '' }
  dialog.value = true
}

async function save() {
  if (!editing.value) return
  try {
    await masterApi.updateMaterial(editing.value.id, {
      name: form.value.name,
      uom: form.value.uom,
      u9ItemCode: form.value.u9ItemCode || undefined,
    })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(msg || '操作失败')
  }
}

/** 后台异步同步（后端分页拉取帆软，避免单次大包超时） */
async function syncFromU9() {
  u9Syncing.value = true
  u9SyncResult.value = null
  const maxPolls = 900
  const intervalMs = 2000
  try {
    const start = await masterApi.startU9SyncJob()
    const jobId = start.data.jobId
    ElMessage.info('已提交后台同步（分页拉取），请稍候…')

    for (let i = 0; i < maxPolls; i++) {
      await new Promise((r) => setTimeout(r, intervalMs))
      const st = await masterApi.getU9SyncJob(jobId)
      const state = st.data.state
      if (state === 'PENDING' || state === 'RUNNING') {
        continue
      }
      if (state === 'FAILED') {
        ElMessage.error(st.data.errorMessage || 'U9 同步失败')
        return
      }
      if (state === 'SUCCESS') {
        const r = st.data.result
        if (r) {
          u9SyncResult.value = r
          u9SyncDialogVisible.value = true
          if (r.errors.length === 0) {
            ElMessage.success(`U9 同步完成：新增 ${r.created}，更新 ${r.updated}`)
          } else {
            ElMessage.warning(`同步完成，有 ${r.errors.length} 条行级提示，请查看详情`)
          }
        } else {
          ElMessage.success('U9 同步已完成')
        }
        await load()
        return
      }
      ElMessage.warning('同步结束但状态异常，请刷新列表核对')
      await load()
      return
    }
    ElMessage.warning('等待结果超时（可刷新页面查看物料是否已写入）')
    await load()
  } catch (e: unknown) {
    let msg = ''
    if (axios.isAxiosError(e)) {
      msg = (e.response?.data as { error?: string } | undefined)?.error ?? e.message
    } else if (e && typeof e === 'object' && 'message' in e) {
      msg = String((e as { message: string }).message)
    }
    ElMessage.error(msg || 'U9 同步失败')
  } finally {
    u9Syncing.value = false
  }
}

/** 帆软 cangku_yigui / cangku_shuiqi → 四厂默认存储仓库（始终本地全部物料） */
async function syncFactoryWarehousesFromU9() {
  factoryWhSyncing.value = true
  factoryWhResult.value = null
  try {
    const r = await masterApi.syncFactoryWarehousesFromU9(undefined)
    factoryWhResult.value = r.data
    factoryWhDialogVisible.value = true
    const d = r.data
    const errN = d.errors.length
    const wrote = d.yiguiUpdated + d.shuiqiUpdated
    const rows = d.yiguiRows + d.shuiqiRows
    if (rows === 0) {
      ElMessage.warning('无物料可同步（本地物料表为空）')
    } else if (wrote === 0) {
      ElMessage.warning(
        '未从帆软取到仓数据（每料号应返回至少一行）；请检查报表与 parameters.code；详情见弹窗',
      )
    } else if (errN === 0) {
      ElMessage.success(
        `四厂仓库已同步：衣柜表 ${d.yiguiUpdated}/${d.yiguiRows}，水漆 ${d.shuiqiUpdated}/${d.shuiqiRows}`,
      )
    } else {
      ElMessage.warning(`已写入 ${wrote} 条，另有 ${errN} 条提示，请查看详情`)
    }
    await load()
  } catch (e: unknown) {
    let msg = ''
    if (axios.isAxiosError(e)) {
      msg = (e.response?.data as { error?: string } | undefined)?.error ?? e.message
    } else if (e && typeof e === 'object' && 'message' in e) {
      msg = String((e as { message: string }).message)
    }
    ElMessage.error(msg || '同步失败')
  } finally {
    factoryWhSyncing.value = false
  }
}

/** 工具栏：帆软 lpgys，本地全部物料 */
async function syncLpgysBulkFromU9() {
  lpgysBulkSyncing.value = true
  lpgysBulkResult.value = null
  try {
    const r = await masterApi.syncSuppliersFromLpgysBulk(undefined)
    lpgysBulkResult.value = r.data
    lpgysBulkDialogVisible.value = true
    const d = r.data
    const errN = d.errors.length
    if (d.materialsTried === 0) {
      ElMessage.warning('无物料可同步（本地物料表为空）')
    } else if (d.supplierLinksUpserted === 0 && errN === 0) {
      ElMessage.warning('未写入供应商关系（报表无有效编码等）')
    } else if (errN === 0) {
      ElMessage.success(
        `供应商已同步：请求 ${d.materialsTried} 个料号，写入 ${d.supplierLinksUpserted} 条关系`,
      )
    } else {
      ElMessage.warning(
        `已处理 ${d.materialsTried} 个料号，写入 ${d.supplierLinksUpserted} 条关系，另有 ${errN} 条提示`,
      )
    }
    await load()
  } catch (e: unknown) {
    let msg = ''
    if (axios.isAxiosError(e)) {
      msg = (e.response?.data as { error?: string } | undefined)?.error ?? e.message
    } else if (e && typeof e === 'object' && 'message' in e) {
      msg = String((e as { message: string }).message)
    }
    ElMessage.error(msg || '同步供应商失败')
  } finally {
    lpgysBulkSyncing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">物料</span>
      <div class="toolbar-actions">
        <el-button :loading="u9Syncing" @click="syncFromU9">从 U9 同步（后台分页）</el-button>
        <div class="factory-wh-bar">
          <el-button
            :loading="factoryWhSyncing"
            :disabled="lpgysBulkSyncing"
            @click="syncFactoryWarehousesFromU9"
          >
            同步四厂仓库（yigui / shuiqi）
          </el-button>
          <el-button
            type="primary"
            :loading="lpgysBulkSyncing"
            :disabled="factoryWhSyncing"
            @click="syncLpgysBulkFromU9"
          >
            同步供应商（lpgys）
          </el-button>
        </div>
      </div>
    </div>
    <p class="hint">物料仅能通过 U9 同步写入系统，不支持新建或 Excel 导入。</p>
    <p class="hint">
      「同步四厂仓库」「同步供应商（lpgys）」均按本地全部物料逐料号请求帆软，全量耗时会较长。
    </p>
    <p class="hint">
      「从 U9 同步」仅拉取物料主数据（wuliao），不再自动跟跑供应商。供应商请用「同步供应商（lpgys）」；需配置 decision-api-url 与 supplier-report-path。
    </p>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" width="120" show-overflow-tooltip />
      <el-table-column prop="uom" label="单位" width="72" />
      <el-table-column prop="purchaseUnitPrice" label="参考单价" width="100" />
      <el-table-column prop="u9WarehouseSuzhou" label="苏州仓" width="100" show-overflow-tooltip />
      <el-table-column prop="u9WarehouseChengdu" label="成都仓" width="100" show-overflow-tooltip />
      <el-table-column prop="u9WarehouseHuanan" label="华南仓" width="100" show-overflow-tooltip />
      <el-table-column prop="u9WarehouseShuiqi" label="水漆仓" width="100" show-overflow-tooltip />
      <el-table-column prop="u9WarehouseNingbo" label="宁波仓" width="100" show-overflow-tooltip />
      <el-table-column prop="u9SupplierName" label="供应商" min-width="120" show-overflow-tooltip />
      <el-table-column prop="u9SupplierCode" label="供应商编码" width="110" show-overflow-tooltip />
      <el-table-column prop="u9ItemCode" label="U9 料号" width="110" />
      <el-table-column label="操作" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="dialog" title="编辑物料" width="480px">
      <el-form label-width="100px">
        <el-form-item label="编码">
          <el-input :model-value="editing?.code" disabled />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.uom" />
        </el-form-item>
        <el-form-item label="U9 料号">
          <el-input v-model="form.u9ItemCode" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="factoryWhDialogVisible" title="四厂仓库同步结果" width="560px">
      <div v-if="factoryWhResult" class="import-result">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="衣柜请求次数">{{ factoryWhResult.yiguiRows }}</el-descriptions-item>
          <el-descriptions-item label="衣柜有数据">{{ factoryWhResult.yiguiUpdated }}</el-descriptions-item>
          <el-descriptions-item label="衣柜空/失败">{{ factoryWhResult.yiguiSkipped }}</el-descriptions-item>
          <el-descriptions-item label="水漆请求次数">{{ factoryWhResult.shuiqiRows }}</el-descriptions-item>
          <el-descriptions-item label="水漆有数据">{{ factoryWhResult.shuiqiUpdated }}</el-descriptions-item>
          <el-descriptions-item label="水漆空/失败">{{ factoryWhResult.shuiqiSkipped }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="factoryWhResult.errors.length" class="import-errors">
          <p style="font-weight:600;color:var(--el-color-warning);margin:12px 0 4px">提示：</p>
          <ul>
            <li v-for="(err, i) in factoryWhResult.errors" :key="i">{{ err }}</li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="lpgysBulkDialogVisible" title="供应商（lpgys）同步结果" width="560px">
      <div v-if="lpgysBulkResult" class="import-result">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="料号请求数">{{ lpgysBulkResult.materialsTried }}</el-descriptions-item>
          <el-descriptions-item label="关系写入数">{{
            lpgysBulkResult.supplierLinksUpserted
          }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="lpgysBulkResult.errors.length" class="import-errors">
          <p style="font-weight:600;color:var(--el-color-warning);margin:12px 0 4px">提示：</p>
          <ul>
            <li v-for="(err, i) in lpgysBulkResult.errors" :key="i">{{ err }}</li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="u9SyncDialogVisible" title="U9 物料同步结果" width="560px">
      <div v-if="u9SyncResult" class="import-result">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="总行数">{{ u9SyncResult.total }}</el-descriptions-item>
          <el-descriptions-item label="新增">{{ u9SyncResult.created }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ u9SyncResult.updated }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ u9SyncResult.skipped }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="u9SyncResult.errors.length" class="import-errors">
          <p style="font-weight:600;color:var(--el-color-warning);margin:12px 0 4px">行级提示：</p>
          <ul>
            <li v-for="(err, i) in u9SyncResult.errors" :key="i">{{ err }}</li>
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
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 12px;
}
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
}
.factory-wh-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 16px;
  line-height: 1.5;
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
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
