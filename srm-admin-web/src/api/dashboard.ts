import { api } from './http'

export type DashboardStats = {
  pendingApprovals: number
  monthPoAmount: string
  pendingReceiveLines: number
  pendingInvoices: number
  unreadUserNotifications: number
}

export type MonthAmount = {
  month: string
  amount: string
}

export type GradeCount = {
  grade: string
  count: number
}

export type PendingItem = {
  instanceId: number
  docType: string
  docNo: string
  amount: string
  createdAt: string
}

export const dashboardApi = {
  stats: (procurementOrgId: number) =>
    api.get<DashboardStats>('/api/v1/dashboard/stats', { params: { procurementOrgId } }),
  poTrend: (procurementOrgId: number) =>
    api.get<MonthAmount[]>('/api/v1/dashboard/po-trend', { params: { procurementOrgId } }),
  perfDistribution: () =>
    api.get<GradeCount[]>('/api/v1/dashboard/perf-distribution'),
  pendingItems: () =>
    api.get<PendingItem[]>('/api/v1/dashboard/pending-items'),
}
