import { api } from './http'

export type PortalInspectionRow = {
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
  totalQty: string
  qualifiedQty: string
  defectQty: string
  defectType: string | null
  remark: string | null
}

export type PortalCorrectiveRow = {
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

export const portalQualityApi = {
  listInspections: () => api.get<PortalInspectionRow[]>('/api/v1/portal/quality/inspections'),
  getInspection: (id: number) => api.get<PortalInspectionRow>(`/api/v1/portal/quality/inspections/${id}`),
  listCorrectiveActions: () => api.get<PortalCorrectiveRow[]>('/api/v1/portal/quality/corrective-actions'),
  getCorrectiveAction: (id: number) =>
    api.get<PortalCorrectiveRow>(`/api/v1/portal/quality/corrective-actions/${id}`),
}
