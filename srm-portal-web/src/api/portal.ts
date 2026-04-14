import { api } from './http'

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
  materialSpec: string | null
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

export type PortalTodoSummary = {
  pendingConfirmLines: number
  asnNoticeCount: number
  /** 已发布且未报价、未过截止日的受邀询价数 */
  pendingRfqQuotations?: number
}

export type PortalRfqSummary = {
  id: number
  rfqNo: string
  title: string
  status: string
  procurementOrgId: number
  procurementOrgCode: string
  publishDate: string | null
  deadline: string | null
}

export type PortalRfqLine = {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  qty: string | number
  uom: string
  specification: string | null
  remark?: string | null
}

export type PortalRfqInvitation = {
  id?: number
  supplierId: number
  supplierCode: string
  supplierName: string
  responded: boolean
}

export type PortalRfqDetail = PortalRfqSummary & {
  remark: string | null
  lines: PortalRfqLine[]
  invitations: PortalRfqInvitation[]
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
  todoSummary: () => api.get<PortalTodoSummary>('/api/v1/portal/todo-summary'),

  listRfq: () => api.get<PortalRfqSummary[]>('/api/v1/portal/rfq'),
  getRfq: (id: number) => api.get<PortalRfqDetail>(`/api/v1/portal/rfq/${id}`),
  submitRfqQuotation: (
    rfqId: number,
    body: {
      currency?: string
      deliveryDays?: number
      validityDays?: number
      remark?: string
      lines: { rfqLineId: number; unitPrice: number; remark?: string }[]
    },
  ) => api.post(`/api/v1/portal/rfq/${rfqId}/quotation`, body),

  listPos: () => api.get<PoSummary[]>('/api/v1/portal/purchase-orders'),
  getPo: (id: number) => api.get<PoDetail>(`/api/v1/portal/purchase-orders/${id}`),
  confirmLine: (lineId: number, body: { confirmedQty: number; promisedDate?: string | null; supplierRemark?: string }) =>
    api.post(`/api/v1/portal/purchase-order-lines/${lineId}/confirm`, body),

  listAsn: () => api.get<AsnNotice[]>('/api/v1/portal/asn-notices'),
  getAsn: (id: number) => api.get<AsnNotice>(`/api/v1/portal/asn-notices/${id}`),
  voidAsn: (id: number) => api.post<AsnNotice>(`/api/v1/portal/asn-notices/${id}/void`),
  createAsn: (body: {
    purchaseOrderId: number
    shipDate: string
    etaDate?: string | null
    carrier?: string | null
    trackingNo?: string | null
    remark?: string | null
    lines: { purchaseOrderLineId: number; shipQty: number }[]
  }) => api.post<AsnNotice>('/api/v1/portal/asn-notices', body),
}
