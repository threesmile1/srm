import { api } from './http'

export type PerfTemplate = {
  id: number
  name: string
  description: string | null
  enabled: boolean
}

export type PerfDimension = {
  id: number
  name: string
  weight: string
  description: string | null
  sortOrder: number
}

export type PerfTemplateDetail = PerfTemplate & {
  dimensions: PerfDimension[]
}

export type EvalSummary = {
  id: number
  supplierId: number
  supplierCode: string
  supplierName: string
  period: string
  totalScore: string | null
  grade: string | null
  status: string
  evaluatorName: string | null
}

export type ScoreItem = {
  id: number
  dimensionId: number
  dimensionName: string
  weight: string
  score: string
  comment: string | null
}

export type EvalDetail = EvalSummary & {
  templateId: number
  templateName: string
  remark: string | null
  scores: ScoreItem[]
}

export const perfApi = {
  listTemplates: () => api.get<PerfTemplate[]>('/api/v1/perf/templates'),
  getTemplate: (id: number) => api.get<PerfTemplateDetail>(`/api/v1/perf/templates/${id}`),
  listEvaluations: (supplierId?: number) =>
    api.get<EvalSummary[]>('/api/v1/perf/evaluations', { params: supplierId ? { supplierId } : {} }),
  getEvaluation: (id: number) => api.get<EvalDetail>(`/api/v1/perf/evaluations/${id}`),
  createEvaluation: (body: {
    supplierId: number
    templateId: number
    period: string
    evaluatorName?: string
    remark?: string
    scores: { dimensionId: number; score: number; comment?: string }[]
  }) => api.post<EvalDetail>('/api/v1/perf/evaluations', body),
  submitEvaluation: (id: number) => api.post<EvalDetail>(`/api/v1/perf/evaluations/${id}/submit`),
  publishEvaluation: (id: number) => api.post<EvalDetail>(`/api/v1/perf/evaluations/${id}/publish`),
}
