import { api } from './http'

export type PortalEvalSummary = {
  id: number
  supplierId: number
  supplierCode: string
  supplierName: string
  period: string
  totalScore: string
  grade: string | null
  status: string
  evaluatorName: string | null
}

export type PortalScoreRow = {
  id: number
  dimensionId: number
  dimensionName: string
  weight: string
  score: string
  comment: string | null
}

export type PortalEvalDetail = {
  id: number
  supplierId: number
  supplierCode: string
  supplierName: string
  templateId: number
  templateName: string
  period: string
  totalScore: string
  grade: string | null
  evaluatorName: string | null
  status: string
  remark: string | null
  scores: PortalScoreRow[]
}

export const portalPerfApi = {
  listEvaluations: () => api.get<PortalEvalSummary[]>('/api/v1/portal/perf/evaluations'),
  getEvaluation: (id: number) => api.get<PortalEvalDetail>(`/api/v1/portal/perf/evaluations/${id}`),
}
