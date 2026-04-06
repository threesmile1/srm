import { api } from './http'

export type ApprovalRule = {
  id: number
  docType: string
  minAmount: string
  maxAmount: string | null
  approvalLevel: number
  approverRole: string
  description: string | null
  enabled: boolean
}

export type ApprovalStep = {
  id: number
  stepLevel: number
  approverRole: string
  approverId: number | null
  approverName: string | null
  action: string | null
  comment: string | null
  actedAt: string | null
}

export type ApprovalInstance = {
  id: number
  docType: string
  docId: number
  docNo: string | null
  totalAmount: string | null
  status: string
  currentLevel: number
  createdAt: string | null
  steps?: ApprovalStep[]
}

export const approvalApi = {
  listRules: () => api.get<ApprovalRule[]>('/api/v1/approvals/rules'),
  saveRule: (body: {
    id?: number
    docType: string
    minAmount?: number
    maxAmount?: number | null
    approvalLevel: number
    approverRole: string
    description?: string
    enabled?: boolean
  }) => api.post<ApprovalRule>('/api/v1/approvals/rules', body),
  deleteRule: (id: number) => api.delete(`/api/v1/approvals/rules/${id}`),
  listInstances: (status?: string) =>
    api.get<ApprovalInstance[]>('/api/v1/approvals/instances', { params: status ? { status } : {} }),
  getInstance: (id: number) => api.get<ApprovalInstance>(`/api/v1/approvals/instances/${id}`),
  getByDoc: (docType: string, docId: number) =>
    api.get<ApprovalInstance | null>('/api/v1/approvals/instances/by-doc', { params: { docType, docId } }),
  processAction: (id: number, body: {
    action: 'APPROVED' | 'REJECTED'
    approverId?: number
    approverName?: string
    comment?: string
  }) => api.post<ApprovalInstance>(`/api/v1/approvals/instances/${id}/action`, body),
}
