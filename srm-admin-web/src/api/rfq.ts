import { api } from './http'
export type RfqSummary = {
  id: number
  rfqNo: string
  title: string
  status: string
  procurementOrgId?: number
  procurementOrgCode: string
  publishDate: string | null
  deadline: string | null
}
export type RfqDetail = RfqSummary & {
  remark: string | null
  lines: {
    id: number
    lineNo: number
    materialCode: string
    materialName: string
    qty: string | number
    uom: string
    specification: string | null
    remark?: string | null
  }[]
  invitations: {
    id?: number
    supplierId: number
    supplierCode: string
    supplierName: string
    responded: boolean
  }[]
}
export type QuotationSummary = {
  id: number
  supplierId: number
  supplierCode: string
  supplierName: string
  totalAmount: string | number
  currency: string
  deliveryDays: number | null
  validityDays: number | null
  submittedAt: string | null
}
export const rfqApi = {
  list: (procurementOrgId: number) => api.get<RfqSummary[]>('/api/v1/rfq', { params: { procurementOrgId } }),
  get: (id: number) => api.get<RfqDetail>(`/api/v1/rfq/${id}`),
  create: (body: { procurementOrgId: number; title: string; deadline?: string; remark?: string; lines: { materialId: number; qty: number; uom: string; specification?: string }[]; supplierIds: number[] }) => api.post<RfqDetail>('/api/v1/rfq', body),
  publish: (id: number) => api.post<RfqDetail>(`/api/v1/rfq/${id}/publish`),
  listQuotations: (rfqId: number) => api.get<QuotationSummary[]>(`/api/v1/rfq/${rfqId}/quotations`),
  award: (rfqId: number, winningSupplierId: number) => api.post(`/api/v1/rfq/${rfqId}/award`, null, { params: { winningSupplierId } }),
}
