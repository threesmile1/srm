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
  /** ORDINARY_VAT | SPECIAL_VAT */
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

export type InvoiceDetail = InvoiceSummary & {
  procurementOrgId: number
  procurementOrgCode: string
  remark: string | null
  lines: InvLineResponse[]
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
  /** 收货 − 已确认发票（核心） */
  diffAmount: string
  /** 订单 − 收货（执行差异） */
  diffPoGrAmount: string
  status: string
  supplierConfirmedAt: string | null
  procurementConfirmedAt: string | null
  varianceAlert: boolean
  disputeReason: string | null
  disputedAt: string | null
  /** SUPPLIER | PROCUREMENT */
  disputedBy: string | null
  procurementRejectReason: string | null
}

export const invoiceApi = {
  list: (params: { procurementOrgId?: number; supplierId?: number }) =>
    api.get<InvoiceSummary[]>('/api/v1/invoices', { params }),
  get: (id: number) => api.get<InvoiceDetail>(`/api/v1/invoices/${id}`),
  create: (body: {
    supplierId: number
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
  }) => api.post<InvoiceDetail>('/api/v1/invoices', body),
  confirm: (id: number) => api.post<InvoiceDetail>(`/api/v1/invoices/${id}/confirm`),
  reject: (id: number, reason?: string) =>
    api.post<InvoiceDetail>(`/api/v1/invoices/${id}/reject`, { reason }),
  listRecon: (params: { procurementOrgId?: number; supplierId?: number }) =>
    api.get<ReconSummary[]>('/api/v1/reconciliations', { params }),
  createRecon: (body: {
    supplierId: number
    procurementOrgId: number
    periodFrom: string
    periodTo: string
    remark?: string
  }) => api.post<ReconSummary>('/api/v1/reconciliations', body),
  confirmRecon: (id: number) => api.post<ReconSummary>(`/api/v1/reconciliations/${id}/confirm`),
  procurementRejectRecon: (id: number, reason: string) =>
    api.post<ReconSummary>(`/api/v1/reconciliations/${id}/procurement-reject`, { reason }),
  procurementDisputeRecon: (id: number, reason: string) =>
    api.post<ReconSummary>(`/api/v1/reconciliations/${id}/procurement-dispute`, { reason }),
  reopenRecon: (id: number) => api.post<ReconSummary>(`/api/v1/reconciliations/${id}/reopen`),
}
