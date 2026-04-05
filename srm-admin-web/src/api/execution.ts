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

export type GrSummary = {
  id: number
  grNo: string
  purchaseOrderId: number
  poNo: string
  warehouseId: number
  warehouseCode: string
  receiptDate: string
  exportStatus: string
}

export type GrDetail = GrSummary & {
  remark: string | null
  lines: GrLine[]
}

export type PurchaseExecutionRow = {
  poNo: string
  poStatus: string
  lineNo: number
  materialCode: string
  materialName: string
  orderedQty: string
  receivedQty: string
  openQty: string
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

  getGoodsReceipt: (id: number) => api.get<GrDetail>(`/api/v1/goods-receipts/${id}`),

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
}
