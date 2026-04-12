import { api } from './http'

export type InvoiceSummary = {
  id: number
  invoiceNo: string
  supplierId: number
  supplierCode: string
  supplierName: string
  invoiceDate: string
  totalAmount: string
  taxAmount: string
  currency: string
  status: string
  invoiceKind: string
  vatInvoiceCode: string | null
  vatInvoiceNumber: string | null
}

export type InvLineResponse = {
  id: number
  lineNo: number
  materialCode: string | null
  materialName: string | null
  qty: string
  unitPrice: string
  amount: string
  taxRate: string | null
  purchaseOrderId: number | null
  poNo: string | null
  goodsReceiptId: number | null
}

export type InvoiceAttachmentItem = {
  id: number
  originalName: string
  contentType: string | null
  fileSize: number
}

export type InvoiceDetail = InvoiceSummary & {
  procurementOrgId: number
  procurementOrgCode: string
  remark: string | null
  lines: InvLineResponse[]
  attachments: InvoiceAttachmentItem[]
}

/** 门户下载附件（需已登录且为发票所属供应商） */
export function portalInvoiceAttachmentDownloadUrl(invoiceId: number, attachmentId: number) {
  const base = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
  return `${base}/api/v1/portal/invoices/${invoiceId}/attachments/${attachmentId}/file`
}

/** 门户开票：可对账 PO 行（甄云类选行） */
export type BillablePoLine = {
  purchaseOrderLineId: number
  purchaseOrderId: number
  poNo: string
  lineNo: number
  materialCode: string
  materialName: string
  receivedQty: string
  invoicedQty: string
  remainingInvoiceableQty: string
  unitPrice: string
  uom: string
}

export type ReconSummary = {
  id: number
  reconNo: string
  supplierId: number
  supplierCode: string
  supplierName: string
  periodFrom: string | null
  periodTo: string | null
  poAmount: string
  grAmount: string
  invoiceAmount: string
  diffAmount: string
  diffPoGrAmount: string
  status: string
  supplierConfirmedAt: string | null
  procurementConfirmedAt: string | null
  varianceAlert: boolean
  disputeReason: string | null
  disputedAt: string | null
  disputedBy: string | null
  procurementRejectReason: string | null
}

export const portalInvoiceApi = {
  list: () => api.get<InvoiceSummary[]>('/api/v1/portal/invoices'),
  billableLines: (procurementOrgId: number) =>
    api.get<BillablePoLine[]>('/api/v1/portal/invoices/billable-lines', { params: { procurementOrgId } }),
  get: (id: number) => api.get<InvoiceDetail>(`/api/v1/portal/invoices/${id}`),
  create: (body: {
    procurementOrgId: number
    invoiceDate: string
    currency?: string
    taxAmount?: number
    remark?: string
    invoiceKind?: string
    vatInvoiceCode?: string
    vatInvoiceNumber?: string
    lines: {
      materialCode?: string
      materialName?: string
      qty: number
      unitPrice: number
      taxRate?: number
      purchaseOrderId?: number
      purchaseOrderLineId?: number
      goodsReceiptId?: number
    }[]
  }) => api.post<InvoiceDetail>('/api/v1/portal/invoices', body),
  uploadAttachment: (invoiceId: number, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<InvoiceAttachmentItem>(`/api/v1/portal/invoices/${invoiceId}/attachments`, fd)
  },
}

export const portalReconApi = {
  list: () => api.get<ReconSummary[]>('/api/v1/portal/reconciliations'),
  /** 甄云类：供应商发起对账 → 待采购确认 */
  create: (body: {
    procurementOrgId: number
    periodFrom: string
    periodTo: string
    remark?: string
  }) => api.post<ReconSummary>('/api/v1/portal/reconciliations', body),
  supplierConfirm: (id: number) =>
    api.post<ReconSummary>(`/api/v1/portal/reconciliations/${id}/supplier-confirm`),
  supplierDispute: (id: number, reason: string) =>
    api.post<ReconSummary>(`/api/v1/portal/reconciliations/${id}/supplier-dispute`, { reason }),
  /** 撤回自行发起、待采购尚未处理的对账单，撤回后列表不再展示，可重新发起对账 */
  withdraw: (id: number) => api.post<ReconSummary>(`/api/v1/portal/reconciliations/${id}/withdraw`),
}
