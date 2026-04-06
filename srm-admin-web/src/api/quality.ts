import { api } from './http'

export type InspectionItem = {
  id: number
  inspectionNo: string
  goodsReceiptId: number
  grNo: string
  supplierId: number
  supplierCode: string
  supplierName: string
  procurementOrgId: number
  procurementOrgCode: string
  inspectionDate: string
  inspectorName: string | null
  result: string
  totalQty: string | number
  qualifiedQty: string | number
  defectQty: string | number
  defectType: string | null
  remark: string | null
}
export type CorrectiveActionItem = {
  id: number
  caNo: string
  inspectionId: number | null
  inspectionNo: string | null
  supplierId: number
  supplierCode: string
  supplierName: string
  procurementOrgId: number
  procurementOrgCode: string
  issueDescription: string
  rootCause: string | null
  correctiveMeasures: string | null
  dueDate: string | null
  status: string
  closedDate: string | null
  remark: string | null
}
export const qualityApi = {
  listInspections: (procurementOrgId: number) => api.get<InspectionItem[]>('/api/v1/quality/inspections', { params: { procurementOrgId } }),
  createInspection: (body: { grId: number; inspectionDate: string; inspectorName?: string; result: string; totalQty: number; qualifiedQty: number; defectQty: number; defectType?: string; remark?: string }) => api.post<InspectionItem>('/api/v1/quality/inspections', body),
  listCAs: (procurementOrgId: number) => api.get<CorrectiveActionItem[]>('/api/v1/quality/corrective-actions', { params: { procurementOrgId } }),
  createCA: (body: { inspectionId?: number; supplierId: number; procurementOrgId: number; issueDescription: string; rootCause?: string; correctiveMeasures?: string; dueDate?: string; remark?: string }) => api.post<CorrectiveActionItem>('/api/v1/quality/corrective-actions', body),
  closeCA: (id: number) => api.post<CorrectiveActionItem>(`/api/v1/quality/corrective-actions/${id}/close`),
}
