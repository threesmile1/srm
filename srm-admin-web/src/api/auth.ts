import { api } from './http'

export type UserInfo = {
  id: number
  username: string
  displayName: string
  roles: string[]
  defaultProcurementOrgId: number | null
  supplierId: number | null
}

export type UserItem = {
  id: number
  username: string
  displayName: string
  enabled: boolean
  defaultProcurementOrgId: number | null
  supplierId: number | null
  roleCodes: string[]
}

export type AuditLogItem = {
  id: number
  userId: number | null
  username: string | null
  action: string
  entityType: string | null
  entityId: number | null
  detail: string | null
  ipAddress: string | null
  createdAt: string
}

export type PageResult<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const authApi = {
  login: (username: string, password: string) =>
    api.post<UserInfo>('/api/v1/auth/login', { username, password }),
  logout: () => api.post('/api/v1/auth/logout'),
  me: () => api.get<UserInfo>('/api/v1/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    api.post('/api/v1/auth/change-password', { oldPassword, newPassword }),
}

export const userApi = {
  list: () => api.get<UserItem[]>('/api/v1/users'),
  create: (body: {
    username: string
    password: string
    displayName?: string
    defaultProcurementOrgId?: number | null
    supplierId?: number | null
    roleCodes?: string[]
  }) => api.post<UserItem>('/api/v1/users', body),
  update: (
    id: number,
    body: {
      displayName?: string
      enabled: boolean
      defaultProcurementOrgId?: number | null
      supplierId?: number | null
      roleCodes?: string[]
    },
  ) => api.put<UserItem>(`/api/v1/users/${id}`, body),
  resetPassword: (id: number, newPassword: string) =>
    api.post(`/api/v1/users/${id}/reset-password`, { newPassword }),
}

export const auditApi = {
  list: (params: { entityType?: string; entityId?: number; page?: number; size?: number }) =>
    api.get<PageResult<AuditLogItem>>('/api/v1/audit-logs', { params }),
}
