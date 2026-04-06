import { api } from './http'

export type PortalNotificationItem = {
  id: number
  title: string
  content: string | null
  category: string
  refType: string | null
  refId: number | null
  read: boolean
  createdAt: string
}

export const portalNotificationApi = {
  list: () => api.get<PortalNotificationItem[]>('/api/v1/portal/notifications'),
  unreadCount: () => api.get<{ count: number }>('/api/v1/portal/notifications/unread-count'),
  markRead: (id: number) => api.post(`/api/v1/portal/notifications/${id}/read`),
  markAllRead: () => api.post('/api/v1/portal/notifications/mark-all-read'),
}
