import { api } from './http'
export type SupplierAuditItem = { id: number; auditType: string; auditDate: string; result: string; score: number | null; auditorName: string | null; remark: string | null }
export const supplierLifecycleApi = {
  updateStatus: (id: number, status: string) => api.post(`/api/v1/suppliers/${id}/lifecycle-status`, { status }),
  listAudits: (id: number) => api.get<SupplierAuditItem[]>(`/api/v1/suppliers/${id}/audits`),
  addAudit: (id: number, body: { auditType: string; auditDate: string; result: string; score?: number; auditorName?: string; remark?: string }) => api.post<SupplierAuditItem>(`/api/v1/suppliers/${id}/audits`, body),
}
