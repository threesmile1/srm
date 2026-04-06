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

export const portalInvoiceApi = {
  list: () => api.get<InvoiceSummary[]>('/api/v1/portal/invoices'),
  get: (id: number) => api.get<InvoiceDetail>(`/api/v1/portal/invoices/${id}`),
  create: (body: {
    procurementOrgId: number
    invoiceDate: string
    currency?: string
    taxAmount?: number
    remark?: string
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
}
