import { api } from './http'

export type PoSummary = {
  id: number
  poNo: string
  officialOrderNo: string | null
  businessDate: string | null
  releasedAt: string | null
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
  requestedDate: string | null
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

export type PoExportRow = {
  businessDate: string | null
  officialOrderNo: string | null
  store2: string | null
  receiverName: string | null
  terminalPhone: string | null
  installAddress: string | null
  materialName: string | null
  materialSpec: string | null
  materialCode: string | null
  supplierName: string | null
  supplierCode: string | null
  docNo: string | null
  docType: string | null
  uom: string | null
  qty: string | number | null
  lastPrice: string | number | null
  negotiatedPrice: string | number | null
  initialPrice: string | number | null
  requestedDate: string | null
  unitPrice: string | number | null
  amount: string | number | null
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

/** Spring Data Page JSON */
export type SpringPage<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
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
  receiverName: string | null
  receiverPhone: string | null
  receiverAddress: string | null
  logisticsAttachmentOriginalName: string | null
  logisticsAttachmentContentType: string | null
  logisticsAttachmentFileSize: number | null
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
  listPosPaged: (page: number, size: number) =>
    api.get<SpringPage<PoSummary>>('/api/v1/portal/purchase-orders/paged', { params: { page, size } }),
  getPo: (id: number) => api.get<PoDetail>(`/api/v1/portal/purchase-orders/${id}`),
  confirmLine: (lineId: number, body: { confirmedQty: number; promisedDate?: string | null; supplierRemark?: string }) =>
    api.post(`/api/v1/portal/purchase-order-lines/${lineId}/confirm`, body),
  exportPoRows: () => api.get<PoExportRow[]>('/api/v1/portal/purchase-orders/export-rows'),

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
    receiverName?: string | null
    receiverPhone?: string | null
    receiverAddress?: string | null
    lines: { purchaseOrderLineId: number; shipQty: number }[]
  }) => api.post<AsnNotice>('/api/v1/portal/asn-notices', body),

  uploadAsnLogisticsAttachment: (asnId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post(`/api/v1/portal/asn-notices/${asnId}/logistics-attachment`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  parseLogisticsByUrl: (url: string) =>
    api.post<{
      carrier: string | null
      trackingNo: string | null
      receiverName: string | null
      receiverPhone: string | null
      receiverAddress: string | null
    }>('/api/v1/portal/logistics/parse-by-url', { url }),
}
