import { api } from './http'

export type PoSummary = {
  id: number
  poNo: string
  status: string
  supplierCode: string
  supplierName: string
  currency: string
  exportStatus: string
}

export type PoLine = {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  qty: string
  receivedQty: string
  uom: string
  unitPrice: string
  amount: string
  requestedDate: string | null
  warehouseId: number
  warehouseCode: string
  confirmedQty: string | null
  promisedDate: string | null
  supplierRemark: string | null
  confirmedAt: string | null
}

export type PoDetail = PoSummary & {
  procurementOrgId: number
  procurementOrgCode: string
  supplierId: number
  revisionNo: number
  remark: string | null
  lines: PoLine[]
}

export type PoImportResult = {
  totalRows: number
  ordersCreated: number
  linesCreated: number
  errors: string[]
}

export type U9PurchaseOrderSyncResult = {
  rowCount: number
  droppedUnmappedRows: number
  groupsTotal: number
  ordersCreated: number
  ordersUpdated: number
  skipped: number
  errors: string[]
  errorReasonCounts: Record<string, number>
}

export const purchaseApi = {
  list: (procurementOrgId: number) =>
    api.get<PoSummary[]>('/api/v1/purchase-orders', { params: { procurementOrgId } }),
  get: (id: number) => api.get<PoDetail>(`/api/v1/purchase-orders/${id}`),
  submit: (id: number) => api.post<PoDetail>(`/api/v1/purchase-orders/${id}/submit`),
  create: (body: {
    procurementOrgId: number
    supplierId: number
    currency?: string
    remark?: string
    lines: {
      materialId: number
      warehouseId: number
      qty: number
      uom?: string
      unitPrice: number
      requestedDate?: string | null
    }[]
  }) => api.post<PoDetail>('/api/v1/purchase-orders', body),
  approve: (id: number) => api.post<PoDetail>(`/api/v1/purchase-orders/${id}/approve`),
  release: (id: number) => api.post<PoDetail>(`/api/v1/purchase-orders/${id}/release`),
  cancel: (id: number) => api.post<PoDetail>(`/api/v1/purchase-orders/${id}/cancel`),
  close: (id: number) => api.post<PoDetail>(`/api/v1/purchase-orders/${id}/close`),
  importOrders: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<PoImportResult>('/api/v1/purchase-orders/import', fd)
  },
  syncFromU9: () =>
    api.post<U9PurchaseOrderSyncResult>('/api/v1/purchase-orders/sync-from-u9', undefined, { timeout: 600_000 }),
}
