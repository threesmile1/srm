<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import {
  masterApi,
  type Material,
  type U9MaterialSyncResult,
  type FactoryWarehouseSyncResult,
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

async function load() {
  const r = await masterApi.listMaterials({
    page: currentPage.value - 1,
    size: pageSize.value,
  })
  rows.value = r.data.content
  total.value = r.data.totalElements
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

/** 帆软 cangku_yigui / cangku_shuiqi → 四厂默认存储仓库 */
async function syncFactoryWarehousesFromU9() {
  factoryWhSyncing.value = true
  factoryWhResult.value = null
  try {
    const r = await masterApi.syncFactoryWarehousesFromU9()
    factoryWhResult.value = r.data
    factoryWhDialogVisible.value = true
    const errN = r.data.errors.length
    if (errN === 0) {
      ElMessage.success(
        `四厂仓库已同步：衣柜表 ${r.data.yiguiUpdated}/${r.data.yiguiRows}，水漆 ${r.data.shuiqiUpdated}/${r.data.shuiqiRows}`,
      )
    } else {
      ElMessage.warning(`已写入，另有 ${errN} 条提示（如无此料号等），请查看详情`)
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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">物料</span>
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <el-button :loading="u9Syncing" @click="syncFromU9">从 U9 同步（后台分页）</el-button>
        <el-button :loading="factoryWhSyncing" @click="syncFactoryWarehousesFromU9"
          >同步四厂仓库（yigui / shuiqi）</el-button
        >
      </div>
    </div>
    <p class="hint">物料仅能通过 U9 同步写入系统，不支持新建或 Excel 导入。</p>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" width="120" show-overflow-tooltip />
      <el-table-column prop="uom" label="单位" width="72" />
      <el-table-column prop="purchaseUnitPrice" label="参考单价" width="100" />
      <el-table-column prop="warehouseSuzhou" label="苏州仓" width="100" show-overflow-tooltip />
      <el-table-column prop="warehouseChengdu" label="成都仓" width="100" show-overflow-tooltip />
      <el-table-column prop="warehouseHuanan" label="华南仓" width="100" show-overflow-tooltip />
      <el-table-column prop="warehouseShuiqi" label="水漆仓" width="100" show-overflow-tooltip />
      <el-table-column prop="u9SupplierName" label="供应商" min-width="120" show-overflow-tooltip />
      <el-table-column prop="u9SupplierCode" label="供应商编码" width="110" show-overflow-tooltip />
      <el-table-column prop="u9ItemCode" label="U9 料号" width="110" />
      <el-table-column label="操作" width="100">
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
          <el-descriptions-item label="衣柜报表行数">{{ factoryWhResult.yiguiRows }}</el-descriptions-item>
          <el-descriptions-item label="衣柜已更新">{{ factoryWhResult.yiguiUpdated }}</el-descriptions-item>
          <el-descriptions-item label="衣柜跳过">{{ factoryWhResult.yiguiSkipped }}</el-descriptions-item>
          <el-descriptions-item label="水漆报表行数">{{ factoryWhResult.shuiqiRows }}</el-descriptions-item>
          <el-descriptions-item label="水漆已更新">{{ factoryWhResult.shuiqiUpdated }}</el-descriptions-item>
          <el-descriptions-item label="水漆跳过">{{ factoryWhResult.shuiqiSkipped }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="factoryWhResult.errors.length" class="import-errors">
          <p style="font-weight:600;color:var(--el-color-warning);margin:12px 0 4px">提示：</p>
          <ul>
            <li v-for="(err, i) in factoryWhResult.errors" :key="i">{{ err }}</li>
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
          <el-descriptions-item label="lpgys 料号请求">{{
            u9SyncResult.lpgysMaterialsTried ?? 0
          }}</el-descriptions-item>
          <el-descriptions-item label="供应商行写入">{{
            u9SyncResult.lpgysSupplierLinksUpserted ?? 0
          }}</el-descriptions-item>
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
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
