import { api } from './http'

const supplierId = () => import.meta.env.VITE_DEV_SUPPLIER_ID || '1'

export type PoSummary = {
  id: number
  poNo: string
  status: string
  supplierCode: string
  supplierName: string
  currency: string
  exportStatus: string
}

export type PoLine = {
  id: number
  lineNo: number
  materialId?: number
  materialCode: string
  materialName: string
  qty: string
  receivedQty: string
  uom: string
  unitPrice: string
  amount: string
  warehouseCode: string
  confirmedQty: string | null
  promisedDate: string | null
  supplierRemark: string | null
  confirmedAt: string | null
}

export type PoDetail = PoSummary & {
  procurementOrgId: number
  procurementOrgCode: string
  supplierId: number
  revisionNo: number
  remark: string | null
  lines: PoLine[]
}

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

export const portalApi = {
  listPos: () =>
    api.get<PoSummary[]>('/api/v1/portal/purchase-orders', {
      params: { supplierId: supplierId() },
    }),
  getPo: (id: number) =>
    api.get<PoDetail>(`/api/v1/portal/purchase-orders/${id}`, {
      params: { supplierId: supplierId() },
    }),
  confirmLine: (lineId: number, body: { confirmedQty: number; promisedDate?: string | null; supplierRemark?: string }) =>
    api.post(`/api/v1/portal/purchase-order-lines/${lineId}/confirm`, body, {
      params: { supplierId: supplierId() },
    }),

  listAsn: () =>
    api.get<AsnNotice[]>('/api/v1/portal/asn-notices', { params: { supplierId: supplierId() } }),
  getAsn: (id: number) =>
    api.get<AsnNotice>(`/api/v1/portal/asn-notices/${id}`, { params: { supplierId: supplierId() } }),
  createAsn: (body: {
    purchaseOrderId: number
    shipDate: string
    etaDate?: string | null
    carrier?: string | null
    trackingNo?: string | null
    remark?: string | null
    lines: { purchaseOrderLineId: number; shipQty: number }[]
  }) =>
    api.post<AsnNotice>('/api/v1/portal/asn-notices', body, { params: { supplierId: supplierId() } }),
}
