<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  portalQualityApi,
  type PortalInspectionRow,
  type PortalCorrectiveRow,
} from '../api/quality'
import DataTableEmpty from '../components/DataTableEmpty.vue'

const route = useRoute()
const router = useRouter()
const tab = ref<'insp' | 'ca'>('ca')

const inspections = ref<PortalInspectionRow[]>([])
const correctives = ref<PortalCorrectiveRow[]>([])
const loadingInsp = ref(false)
const loadingCa = ref(false)

const inspDialog = ref(false)
const caDialog = ref(false)
const inspDetail = ref<PortalInspectionRow | null>(null)
const caDetail = ref<PortalCorrectiveRow | null>(null)

async function loadInspections() {
  loadingInsp.value = true
  try {
    inspections.value = (await portalQualityApi.listInspections()).data
  } finally {
    loadingInsp.value = false
  }
}

async function loadCorrectives() {
  loadingCa.value = true
  try {
    correctives.value = (await portalQualityApi.listCorrectiveActions()).data
  } finally {
    loadingCa.value = false
  }
}

async function applyQuery() {
  const iid = route.query.inspectionId
  const cid = route.query.caId
  if (iid != null && iid !== '') {
    tab.value = 'insp'
    try {
      inspDetail.value = (await portalQualityApi.getInspection(Number(iid))).data
      inspDialog.value = true
    } catch {
      inspDetail.value = null
    }
    router.replace({ path: '/quality', query: {} })
  } else if (cid != null && cid !== '') {
    tab.value = 'ca'
    try {
      caDetail.value = (await portalQualityApi.getCorrectiveAction(Number(cid))).data
      caDialog.value = true
    } catch {
      caDetail.value = null
    }
    router.replace({ path: '/quality', query: {} })
  }
}

onMounted(async () => {
  await Promise.all([loadInspections(), loadCorrectives()])
  await applyQuery()
})

watch(() => route.query, applyQuery)

const caStatusMap: Record<string, string> = {
  OPEN: '待处理', IN_PROGRESS: '进行中', CLOSED: '已关闭', OVERDUE: '逾期',
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">质量协同</span>
      <el-button type="primary" @click="loadInspections(); loadCorrectives()">刷新</el-button>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="纠正措施" name="ca">
        <el-table v-loading="loadingCa" :data="correctives" stripe>
          <template #empty>
            <DataTableEmpty />
          </template>
          <el-table-column prop="caNo" label="编号" width="150" />
          <el-table-column prop="issueDescription" label="问题描述" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ caStatusMap[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="dueDate" label="要求完成" width="120" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="caDetail = row; caDialog = true">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="质检记录" name="insp">
        <el-table v-loading="loadingInsp" :data="inspections" stripe>
          <template #empty>
            <DataTableEmpty />
          </template>
          <el-table-column prop="inspectionNo" label="质检单号" width="150" />
          <el-table-column prop="grNo" label="收货单" width="130" />
          <el-table-column prop="inspectionDate" label="日期" width="110" />
          <el-table-column prop="result" label="结果" width="100" />
          <el-table-column prop="qualifiedQty" label="合格量" width="100" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="inspDetail = row; inspDialog = true">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="inspDialog" title="质检详情" width="640px" v-if="inspDetail">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="单号">{{ inspDetail.inspectionNo }}</el-descriptions-item>
        <el-descriptions-item label="收货单">{{ inspDetail.grNo }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ inspDetail.inspectionDate }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ inspDetail.result }}</el-descriptions-item>
        <el-descriptions-item label="检验量">{{ inspDetail.totalQty }}</el-descriptions-item>
        <el-descriptions-item label="合格量">{{ inspDetail.qualifiedQty }}</el-descriptions-item>
        <el-descriptions-item label="不良量">{{ inspDetail.defectQty }}</el-descriptions-item>
        <el-descriptions-item label="不良类型">{{ inspDetail.defectType || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ inspDetail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="caDialog" title="纠正措施" width="720px" v-if="caDetail">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="编号">{{ caDetail.caNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ caStatusMap[caDetail.status] || caDetail.status }}</el-descriptions-item>
        <el-descriptions-item label="问题">{{ caDetail.issueDescription }}</el-descriptions-item>
        <el-descriptions-item label="根因">{{ caDetail.rootCause || '—' }}</el-descriptions-item>
        <el-descriptions-item label="措施">{{ caDetail.correctiveMeasures || '—' }}</el-descriptions-item>
        <el-descriptions-item label="要求完成">{{ caDetail.dueDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关闭日">{{ caDetail.closedDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ caDetail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
.title { font-size: 18px; font-weight: 600; }
</style>
