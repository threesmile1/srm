import { api } from './http'
export type NotificationItem = {
  id: number
  title: string
  content: string | null
  category: string
  refType: string | null
  refId: number | null
  read: boolean
  createdAt: string
}
/** 列表/未读数均绑定当前会话用户，不再传 userId */
export const notificationApi = {
  list: () => api.get<NotificationItem[]>('/api/v1/notifications'),
  unreadCount: () => api.get<{ count: number }>('/api/v1/notifications/unread-count'),
  markRead: (id: number) => api.post(`/api/v1/notifications/${id}/read`),
  markAllRead: () => api.post('/api/v1/notifications/mark-all-read'),
}
