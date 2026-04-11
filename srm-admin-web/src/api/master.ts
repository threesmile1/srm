import { api } from './http'

/** 帆软/U9 取数可能超过 30s，与后端 srm.u9.http-read-timeout-ms 对齐 */
const U9_SYNC_TIMEOUT_MS = 180_000

export type Supplier = {
  id: number
  code: string
  name: string
  u9VendorCode: string | null
  taxId: string | null
  lifecycleStatus: string | null
  procurementOrgIds: number[]
}

export type Material = {
  id: number
  code: string
  name: string
  uom: string
  u9ItemCode: string | null
  specification: string | null
  purchaseUnitPrice: string | number | null
  u9WarehouseName: string | null
  u9SupplierCode: string | null
  u9SupplierName: string | null
}

export type U9MaterialSyncResult = {
  total: number
  created: number
  updated: number
  skipped: number
  errors: string[]
}

/** 异步同步任务状态（轮询 GET jobs/{jobId}） */
export type U9SyncJobStatus = {
  jobId: string
  state: string
  result: U9MaterialSyncResult | null
  errorMessage: string | null
  createdAtEpochMs: number
  finishedAtEpochMs: number | null
}

export type ImportResult = {
  total: number
  created: number
  updated: number
  skipped: number
  errors: string[]
}

export const masterApi = {
  listSuppliers: () => api.get<Supplier[]>('/api/v1/master/suppliers'),
  createSupplier: (body: {
    code: string
    name: string
    u9VendorCode?: string
    taxId?: string
    procurementOrgIds: number[]
  }) => api.post<Supplier>('/api/v1/master/suppliers', body),
  updateSupplier: (
    id: number,
    body: { name: string; u9VendorCode?: string; taxId?: string; procurementOrgIds: number[] },
  ) => api.put<Supplier>(`/api/v1/master/suppliers/${id}`, body),
  importSuppliers: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<ImportResult>('/api/v1/master/suppliers/import', fd)
  },
  listMaterials: () => api.get<Material[]>('/api/v1/master/materials'),
  createMaterial: (body: { code: string; name: string; uom: string; u9ItemCode?: string }) =>
    api.post<Material>('/api/v1/master/materials', body),
  updateMaterial: (id: number, body: { name: string; uom: string; u9ItemCode?: string }) =>
    api.put<Material>(`/api/v1/master/materials/${id}`, body),
  importMaterials: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post<ImportResult>('/api/v1/master/materials/import', fd)
  },
  /** 同步拉取（可能较慢；大数据建议用 startU9SyncJob） */
  syncMaterialsFromU9: (body?: unknown) =>
    body === undefined
      ? api.post<U9MaterialSyncResult>('/api/v1/master/materials/sync-from-u9', undefined, {
          timeout: U9_SYNC_TIMEOUT_MS,
        })
      : api.post<U9MaterialSyncResult>('/api/v1/master/materials/sync-from-u9', body, {
          timeout: U9_SYNC_TIMEOUT_MS,
        }),
  /** 异步全量同步：立即返回 jobId */
  startU9SyncJob: () =>
    api.post<{ jobId: string }>('/api/v1/master/materials/sync-from-u9/async'),
  getU9SyncJob: (jobId: string) =>
    api.get<U9SyncJobStatus>(`/api/v1/master/materials/sync-from-u9/jobs/${jobId}`, { timeout: 15000 }),
}
