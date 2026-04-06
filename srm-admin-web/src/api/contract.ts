import { api } from './http'
export type ContractSummary = { id: number; contractNo: string; title: string; supplierCode: string; supplierName: string; status: string; contractType: string; startDate: string | null; endDate: string | null; totalAmount: string | null; currency: string }
export type ContractDetail = ContractSummary & { procurementOrgId: number; procurementOrgCode: string; remark: string | null; lines: { id: number; lineNo: number; materialCode: string | null; materialName: string | null; materialDesc: string | null; qty: string | null; uom: string | null; unitPrice: string | null; amount: string | null }[] }
export const contractApi = {
  list: (procurementOrgId: number) => api.get<ContractSummary[]>('/api/v1/contracts', { params: { procurementOrgId } }),
  get: (id: number) => api.get<ContractDetail>(`/api/v1/contracts/${id}`),
  create: (body: { supplierId: number; procurementOrgId: number; title: string; contractType?: string; startDate?: string; endDate?: string; currency?: string; remark?: string; lines: { materialId?: number; materialDesc?: string; qty?: number; uom?: string; unitPrice?: number }[] }) => api.post<ContractDetail>('/api/v1/contracts', body),
  activate: (id: number) => api.post<ContractDetail>(`/api/v1/contracts/${id}/activate`),
  terminate: (id: number) => api.post<ContractDetail>(`/api/v1/contracts/${id}/terminate`),
  listExpiring: (procurementOrgId: number, daysAhead?: number) => api.get<ContractSummary[]>('/api/v1/contracts/expiring', { params: { procurementOrgId, daysAhead } }),
}
