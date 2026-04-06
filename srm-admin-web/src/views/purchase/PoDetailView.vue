<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { purchaseApi, type PoDetail } from '../../api/purchase'
import { approvalApi, type ApprovalInstance } from '../../api/approval'
import { executionApi, type AsnNotice } from '../../api/execution'

const route = useRoute()
const router = useRouter()
const po = ref<PoDetail | null>(null)
const asnList = ref<AsnNotice[]>([])
const approvalInst = ref<ApprovalInstance | null | undefined>(undefined)
const tab = ref<'lines' | 'asn'>('lines')

async function load() {
  const id = Number(route.params.id)
  const r = await purchaseApi.get(id)
  po.value = r.data
  if (po.value?.status === 'PENDING_APPROVAL') {
    try {
      const ar = await approvalApi.getByDoc('PO', id)
      approvalInst.value = ar.data
    } catch {
      approvalInst.value = null
    }
  } else {
    approvalInst.value = undefined
  }
}

async function loadAsn() {
  if (!po.value) return
  try {
    const r = await executionApi.listAsn(po.value.id)
    asnList.value = r.data
  } catch {
    asnList.value = []
  }
}

watch(tab, (t) => {
  if (t === 'asn') loadAsn()
})

async function act(fn: () => Promise<unknown>, msg: string) {
  try {
    await fn()
    ElMessage.success(msg)
    await load()
    await loadAsn()
  } catch (e: unknown) {
    const err = e && typeof e === 'object' && 'response' in e ? (e as { response?: { data?: { error?: string } } }).response?.data?.error : ''
    ElMessage.error(err || '失败')
  }
}

onMounted(async () => {
  await load()
  await loadAsn()
})
</script>

<template>
  <div v-if="po" class="page">
    <div class="toolbar">
      <el-button @click="router.push('/purchase/orders')">返回列表</el-button>
      <span class="title">{{ po.poNo }} · {{ po.status }}</span>
      <el-button
        v-if="po.status === 'RELEASED'"
        type="primary"
        plain
        @click="
          router.push({
            path: '/purchase/receipts/new',
            query: { procurementOrgId: String(po.procurementOrgId), poId: String(po.id) },
          })
        "
      >
        录入收货
      </el-button>
    </div>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="采购组织">{{ po.procurementOrgCode }}</el-descriptions-item>
      <el-descriptions-item label="供应商">{{ po.supplierCode }}</el-descriptions-item>
      <el-descriptions-item label="币种">{{ po.currency }}</el-descriptions-item>
      <el-descriptions-item label="修订">{{ po.revisionNo }}</el-descriptions-item>
      <el-descriptions-item label="U9导出">{{ po.exportStatus }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ po.remark || '—' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert
      v-if="po.status === 'PENDING_APPROVAL' && approvalInst?.status === 'PENDING'"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="该订单已在审批流程中，请在「审批中心」处理。"
    >
      <el-button type="primary" link @click="router.push('/approval/list')">前往审批中心</el-button>
    </el-alert>
    <div class="actions">
      <el-button
        v-if="po.status === 'DRAFT'"
        type="primary"
        @click="act(() => purchaseApi.submit(po!.id), '已提交审批')"
      >
        提交审批
      </el-button>
      <el-button v-if="po.status === 'APPROVED'" type="success" @click="act(() => purchaseApi.release(po!.id), '已发布')">
        发布供应商
      </el-button>
      <el-button v-if="po.status === 'DRAFT' || po.status === 'APPROVED'" type="danger" plain @click="act(() => purchaseApi.cancel(po!.id), '已取消')">
        取消
      </el-button>
      <el-button v-if="po.status === 'RELEASED'" @click="act(() => purchaseApi.close(po!.id), '已关闭')">关闭</el-button>
    </div>

    <el-tabs v-model="tab" style="margin-top: 16px">
      <el-tab-pane label="订单行" name="lines">
        <el-table :data="po.lines" stripe>
          <el-table-column prop="lineNo" label="行" width="60" />
          <el-table-column prop="materialCode" label="物料" width="120" />
          <el-table-column prop="materialName" label="名称" />
          <el-table-column prop="qty" label="数量" width="100" />
          <el-table-column prop="receivedQty" label="已收" width="90" />
          <el-table-column prop="uom" label="单位" width="70" />
          <el-table-column prop="unitPrice" label="单价" width="100" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="warehouseCode" label="仓库" width="100" />
          <el-table-column prop="confirmedQty" label="确认数量" width="100" />
          <el-table-column prop="promisedDate" label="承诺交期" width="120" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="发货通知 (ASN)" name="asn">
        <el-table :data="asnList" stripe>
          <el-table-column prop="asnNo" label="ASN 单号" width="160" />
          <el-table-column prop="shipDate" label="发货日" width="120" />
          <el-table-column prop="etaDate" label="预计到货" width="120" />
          <el-table-column prop="carrier" label="承运商" />
          <el-table-column prop="trackingNo" label="运单号" width="140" />
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-table :data="row.lines" size="small">
                <el-table-column prop="poLineNo" label="订单行" width="80" />
                <el-table-column prop="materialCode" label="物料" width="120" />
                <el-table-column prop="materialName" label="名称" />
                <el-table-column prop="shipQty" label="发货量" width="100" />
              </el-table>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
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
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
