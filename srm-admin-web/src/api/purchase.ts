import { api } from './http'

export type PoSummary = {
  id: number
  poNo: string
  u9DocNo: string | null
  officialOrderNo: string | null
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
  materialSpec: string | null
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

/** Spring Data Page JSON */
export type SpringPage<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
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
  listPaged: (
    procurementOrgId: number,
    page: number,
    size: number,
    filters?: { poNo?: string; u9DocNo?: string; officialOrderNo?: string },
  ) => {
    const params: Record<string, string | number> = { procurementOrgId, page, size }
    const f = filters ?? {}
    const po = f.poNo?.trim()
    const u9 = f.u9DocNo?.trim()
    const off = f.officialOrderNo?.trim()
    if (po) params.poNo = po
    if (u9) params.u9DocNo = u9
    if (off) params.officialOrderNo = off
    return api.get<SpringPage<PoSummary>>('/api/v1/purchase-orders/paged', { params })
  },
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
  reopen: (id: number) => api.post<PoDetail>(`/api/v1/purchase-orders/${id}/reopen`),
  importOrders: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<PoImportResult>('/api/v1/purchase-orders/import', fd)
  },
  syncFromU9: (procurementOrgId: number) =>
    api.post<U9PurchaseOrderSyncResult>('/api/v1/purchase-orders/sync-from-u9', undefined, {
      params: { procurementOrgId },
      timeout: 600_000,
    }),
}
