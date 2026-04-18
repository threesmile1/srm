import { api } from './http'

export type AsnLine = {
  id: number
  lineNo: number
  purchaseOrderLineId: number
  poLineNo: number
  materialCode: string
  materialName: string
  shipQty: string
}

export type AsnNotice = {
  id: number
  asnNo: string
  purchaseOrderId: number
  poNo: string
  status: string
  shipDate: string
  etaDate: string | null
  carrier: string | null
  trackingNo: string | null
  remark: string | null
  lines: AsnLine[]
}

export type GrLine = {
  id: number
  lineNo: number
  purchaseOrderLineId: number
  poLineNo: number
  asnLineId: number | null
  asnNo: string | null
  materialCode: string
  receivedQty: string
  uom: string
}

export type U9GoodsReceiptSyncResult = {
  rowCount: number
  droppedUnmappedRows: number
  groupsTotal: number
  created: number
  updatedStatusOnly: number
  skippedNonNingbo: number
  skipped: number
  errors: string[]
}

export type GrSummary = {
  id: number
  grNo: string
  /** 本收货单 U9 单据编号（帆软同步） */
  u9DocNo?: string | null
  purchaseOrderId: number
  poNo: string
  /** 关联采购订单的 U9 单号（PM DocNo） */
  poU9DocNo?: string | null
  warehouseId: number
  warehouseCode: string
  receiptDate: string
  exportStatus: string
  /** PENDING_APPROVAL：待客服审核（宁波公司）；APPROVED；REJECTED */
  status: string
  /** 关联采购订单尚未收清数量（列表接口返回；详情可选） */
  pendingReceiptQty?: string
  /** 是否存在至少一行关联发货通知 ASN（列表接口返回） */
  hasAsnShipment?: boolean
  /** 关联订单是否存在已提交的发货通知（收货行可能尚未关联 ASN） */
  purchaseOrderHasSubmittedAsn?: boolean
  /** 本收货单关联的发货通知单号，多份时逗号分隔 */
  asnSummary?: string
}

/** 已有发货通知、尚未创建任何收货单的采购订单（列表「待收货的发货通知」待建收货） */
export type OpenPoAsnReceiptHint = {
  purchaseOrderId: number
  poNo: string
  poU9DocNo?: string | null
  asnNo: string
  /** 关联「最新已提交」发货通知 id，用于宁波客服确认跳转审批 */
  asnNoticeId: number
  pendingReceiptQty: string
}

export type GrDetail = GrSummary & {
  remark: string | null
  lines: GrLine[]
}

export type PurchaseExecutionRow = {
  poNo: string
  u9DocNo: string | null
  poStatus: string
  businessDate: string | null
  officialOrderNo: string | null
  store2: string | null
  receiverName: string | null
  terminalPhone: string | null
  installAddress: string | null
  lineNo: number
  materialCode: string
  materialName: string
  orderedQty: string
  receivedQty: string
  openQty: string
}

/** Spring Data Page JSON */
export type SpringPage<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type ReportMonthAmount = {
  month: string
  amount: number
}

export type SupplierShareRow = {
  supplierCode: string
  supplierName: string
  amount: number
  sharePercent: number
}

export type DeliveryAchievement = {
  completedOnTime: number
  completedLate: number
  openWithDueDate: number
  onTimeRatePercent: number
}

export type PriceAnalysisRow = {
  materialCode: string
  materialName: string
  minUnitPrice: number
  maxUnitPrice: number
  avgUnitPrice: number
  lineCount: number
  totalAmount: number
  volatilityPercent: number
}

const xlsxMime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

export function downloadArrayBuffer(buf: ArrayBuffer, filename: string, mime = xlsxMime) {
  const blob = new Blob([buf], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export const executionApi = {
  listAsn: (purchaseOrderId: number) =>
    api.get<AsnNotice[]>(`/api/v1/purchase-orders/${purchaseOrderId}/asn-notices`),

  listGoodsReceipts: (procurementOrgId: number) =>
    api.get<GrSummary[]>('/api/v1/goods-receipts', { params: { procurementOrgId } }),

  listGoodsReceiptsPaged: (procurementOrgId: number, page: number, size: number, waitReceiveOnly = false) =>
    api.get<SpringPage<GrSummary>>('/api/v1/goods-receipts/paged', {
      params: { procurementOrgId, page, size, waitReceiveOnly },
    }),

  /** 有已提交 ASN、本组织下尚未建过收货单的订单（用于待收货页签） */
  listPendingOpenPoWithAsn: (procurementOrgId: number) =>
    api.get<OpenPoAsnReceiptHint[]>('/api/v1/goods-receipts/pending-open-po-with-asn', {
      params: { procurementOrgId },
    }),

  getGoodsReceipt: (id: number) => api.get<GrDetail>(`/api/v1/goods-receipts/${id}`),

  syncGoodsReceiptsFromU9: (procurementOrgId: number) =>
    api.post<U9GoodsReceiptSyncResult>('/api/v1/goods-receipts/sync-from-u9', undefined, {
      params: { procurementOrgId },
      timeout: 600_000,
    }),

  createGoodsReceipt: (body: {
    procurementOrgId: number
    purchaseOrderId: number
    warehouseId: number
    receiptDate: string
    remark?: string | null
    lines: { purchaseOrderLineId: number; receivedQty: number; asnLineId?: number | null }[]
  }) => api.post<GrDetail>('/api/v1/goods-receipts', body),

  exportPurchaseOrders: (ids: number[]) =>
    api.post<ArrayBuffer>('/api/v1/exports/purchase-orders', ids, { responseType: 'arraybuffer' }),

  exportGoodsReceipts: (ids: number[]) =>
    api.post<ArrayBuffer>('/api/v1/exports/goods-receipts', ids, { responseType: 'arraybuffer' }),

  purchaseExecutionReport: (procurementOrgId: number) =>
    api.get<PurchaseExecutionRow[]>('/api/v1/reports/purchase-execution', { params: { procurementOrgId } }),

  purchaseExecutionReportPaged: (
    procurementOrgId: number,
    page: number,
    size: number,
    q?: { poNo?: string; u9DocNo?: string; officialOrderNo?: string },
  ) =>
    api.get<SpringPage<PurchaseExecutionRow>>('/api/v1/reports/purchase-execution/paged', {
      params: { procurementOrgId, page, size, ...(q || {}) },
    }),

  purchaseAmountTrend: (procurementOrgId: number, months = 12) =>
    api.get<ReportMonthAmount[]>('/api/v1/reports/analytics/purchase-amount-trend', {
      params: { procurementOrgId, months },
    }),

  supplierShare: (procurementOrgId: number, from: string, to: string) =>
    api.get<SupplierShareRow[]>('/api/v1/reports/analytics/supplier-share', {
      params: { procurementOrgId, from, to },
    }),

  deliveryAchievement: (procurementOrgId: number) =>
    api.get<DeliveryAchievement>('/api/v1/reports/analytics/delivery-achievement', {
      params: { procurementOrgId },
    }),

  priceAnalysis: (procurementOrgId: number, from: string, limit = 20) =>
    api.get<PriceAnalysisRow[]>('/api/v1/reports/analytics/price-analysis', {
      params: { procurementOrgId, from, limit },
    }),
}
