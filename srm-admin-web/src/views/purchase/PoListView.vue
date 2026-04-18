<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { foundationApi, type OrgUnit } from '../../api/foundation'
import { purchaseApi, type PoSummary } from '../../api/purchase'
import { usePersistedProcurementOrg } from '../../composables/usePersistedProcurementOrg'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const router = useRouter()
const orgs = ref<OrgUnit[]>([])
const orgId = ref<number | null>(null)
usePersistedProcurementOrg(orgId, orgs, 'po-list')
const rows = ref<PoSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const u9PoSyncing = ref(false)

/** 列表筛选（模糊匹配，不区分大小写） */
const filterPoNo = ref('')
const filterU9DocNo = ref('')
const filterOfficialOrderNo = ref('')

/** 仅宁波公司采购组织展示「从 U9 同步采购订单（帆软）」（与 org_unit.code / name / u9_org_code 一致即可） */
function isNingboProcurementOrg(o: OrgUnit | null | undefined): boolean {
  if (!o) return false
  const code = (o.code ?? '').trim().toUpperCase()
  const name = (o.name ?? '').trim()
  const u9 = (o.u9OrgCode ?? '').trim()
  return code === 'NB' || name === '宁波公司' || u9 === '1001711275375071'
}

const currentProcurementOrg = computed(() => orgs.value.find((x) => x.id === orgId.value) ?? null)

const showU9FineReportPoSync = computed(() => isNingboProcurementOrg(currentProcurementOrg.value))

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

function listFilters() {
  return {
    poNo: filterPoNo.value,
    u9DocNo: filterU9DocNo.value,
    officialOrderNo: filterOfficialOrderNo.value,
  }
}

async function loadPos() {
  if (orgId.value == null) return
  try {
    const r = await purchaseApi.listPaged(orgId.value, currentPage.value - 1, pageSize.value, listFilters())
    rows.value = r.data.content
    total.value = r.data.totalElements
  } catch (e: unknown) {
    // 兼容后端未重启/旧版本尚无 paged 接口的情况，避免“无数据但无提示”
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { error?: string } } }).response?.data?.error
        : ''
    ElMessage.warning(msg || '分页加载失败，已回退为全量加载（请重启后端或刷新）')
    try {
      const r = await purchaseApi.list(orgId.value)
      rows.value = r.data
      total.value = r.data.length
    } catch (e2: unknown) {
      const msg2 =
        e2 && typeof e2 === 'object' && 'response' in e2
          ? (e2 as { response?: { data?: { error?: string } } }).response?.data?.error
          : ''
      ElMessage.error(msg2 || '加载失败（请检查是否登录过期/后端是否已启动）')
      rows.value = []
      total.value = 0
    }
  }
}

watch(orgId, () => {
  filterPoNo.value = ''
  filterU9DocNo.value = ''
  filterOfficialOrderNo.value = ''
  currentPage.value = 1
  loadPos()
})

watch([currentPage, pageSize], () => {
  loadPos()
})

onMounted(async () => {
  await loadOrgs()
  await loadPos()
})

function goDetail(id: number) {
  router.push(`/purchase/orders/${id}`)
}

function applyPoFilters() {
  currentPage.value = 1
  loadPos()
}

function resetPoFilters() {
  filterPoNo.value = ''
  filterU9DocNo.value = ''
  filterOfficialOrderNo.value = ''
  currentPage.value = 1
  loadPos()
}

async function syncPurchaseOrdersFromU9() {
  if (!showU9FineReportPoSync.value) {
    ElMessage.warning('请先在上方选择「宁波公司」采购组织')
    return
  }
  if (orgId.value == null) {
    ElMessage.warning('请先选择采购组织')
    return
  }
  u9PoSyncing.value = true
  try {
    const r = await purchaseApi.syncFromU9(orgId.value)
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
        parts.push(`未归组 ${dropped} 行（缺单据编号列）`)
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
          `有 ${dropped} 行帆软数据未参与归组：缺少「单据编号」列值，或列名与模板不一致（后端已支持列名大小写不敏感）。`,
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

</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">采购订单</span>
      <el-select v-model="orgId" placeholder="采购组织" style="width: 220px">
        <el-option v-for="o in orgs" :key="o.id" :label="`${o.code} ${o.name}`" :value="o.id" />
      </el-select>
      <el-button type="primary" @click="$router.push('/purchase/orders/new')">新建</el-button>
      <el-button
        v-if="showU9FineReportPoSync"
        type="success"
        plain
        :loading="u9PoSyncing"
        @click="syncPurchaseOrdersFromU9"
      >
        从 U9 同步采购订单（帆软）
      </el-button>
    </div>
    <div class="filter-row">
      <el-input
        v-model="filterPoNo"
        clearable
        placeholder="订单号"
        style="width: 180px"
        @keyup.enter="applyPoFilters"
      />
      <el-input
        v-model="filterU9DocNo"
        clearable
        placeholder="U9单号"
        style="width: 180px"
        @keyup.enter="applyPoFilters"
      />
      <el-input
        v-model="filterOfficialOrderNo"
        clearable
        placeholder="正式订单号"
        style="width: 200px"
        @keyup.enter="applyPoFilters"
      />
      <el-button type="primary" @click="applyPoFilters">查询</el-button>
      <el-button @click="resetPoFilters">重置</el-button>
    </div>
    <el-table :data="rows" stripe @row-dblclick="(row: PoSummary) => goDetail(row.id)">
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="poNo" label="订单号" width="200" />
      <el-table-column prop="u9DocNo" label="U9单号" width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="supplierCode" label="供应商" width="120" />
      <el-table-column prop="supplierName" label="供应商名称" />
      <el-table-column prop="officialOrderNo" label="正式订单号" min-width="260" show-overflow-tooltip />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="exportStatus" label="导出" width="100" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
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
      />
    </div>
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
.pager-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
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
