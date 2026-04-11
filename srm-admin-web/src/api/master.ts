import { api } from './http'

/** 帆软/U9 全量或四厂报表可能很慢，与后端 srm.u9.http-read-timeout-ms 默认对齐（10 分钟） */
const U9_SYNC_TIMEOUT_MS = 600_000

/** 按料号逐次调帆软，全量物料时总耗时长；0 表示 Axios 不限制单次请求超时 */
const FACTORY_WAREHOUSE_SYNC_TIMEOUT_MS = 0

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
  /** 苏州 / 成都 / 华南 / 水漆 工厂默认存储仓库（帆软 cangku_yigui / cangku_shuiqi） */
  u9WarehouseSuzhou: string | null
  u9WarehouseChengdu: string | null
  u9WarehouseHuanan: string | null
  u9WarehouseShuiqi: string | null
  u9SupplierCode: string | null
  u9SupplierName: string | null
}

export type U9MaterialSyncResult = {
  total: number
  created: number
  updated: number
  skipped: number
  errors: string[]
  /** lpgys.cpt 按料号请求次数 */
  lpgysMaterialsTried?: number
  /** material_supplier_u9 写入行数（一料多供可大于 tried） */
  lpgysSupplierLinksUpserted?: number
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

/** Spring Data Page JSON */
export type SpringPage<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

/** 物料表聚合的仓库名（U9/苏州/成都/华南/水漆） */
export type MaterialWarehouseRef = {
  scope: string
  warehouseName: string
  materialCount: number
}

/** 物料中出现的供应商（快照 + material_supplier_u9） */
export type MaterialSupplierRef = {
  u9SupplierCode: string
  u9SupplierName: string | null
  refCount: number
}

export type FactoryWarehouseSyncResult = {
  yiguiRows: number
  yiguiUpdated: number
  yiguiSkipped: number
  shuiqiRows: number
  shuiqiUpdated: number
  shuiqiSkipped: number
  errors: string[]
}

/** 批量 lpgys（全部物料或指定编码列表） */
export type U9LpgysBulkSyncResult = {
  materialsTried: number
  supplierLinksUpserted: number
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
  /** 物料中出现的供应商（只读聚合） */
  listSuppliersMaterialDerived: (params: { page: number; size: number }) =>
    api.get<SpringPage<MaterialSupplierRef>>('/api/v1/master/suppliers/material-derived', {
      params: { page: params.page, size: params.size },
    }),
  /** 分页列表：默认每页 10，可选 20 / 50（非法 size 时后端按 10 处理） */
  listMaterials: (params: { page: number; size: number }) =>
    api.get<SpringPage<Material>>('/api/v1/master/materials', {
      params: { page: params.page, size: params.size },
    }),
  /** 下拉框等需全量物料 */
  listAllMaterialsForSelect: () => api.get<Material[]>('/api/v1/master/materials/all'),
  updateMaterial: (id: number, body: { name: string; uom: string; u9ItemCode?: string }) =>
    api.put<Material>(`/api/v1/master/materials/${id}`, body),
  /** 同步拉取（可能较慢；大数据建议用 startU9SyncJob） */
  syncMaterialsFromU9: (body?: unknown) =>
    body === undefined
      ? api.post<U9MaterialSyncResult>('/api/v1/master/materials/sync-from-u9', undefined, {
          timeout: U9_SYNC_TIMEOUT_MS,
        })
      : api.post<U9MaterialSyncResult>('/api/v1/master/materials/sync-from-u9', body, {
          timeout: U9_SYNC_TIMEOUT_MS,
        }),
  /** 异步全量同步：立即返回 jobId（后端建任务可能略慢，略加长超时） */
  startU9SyncJob: () =>
    api.post<{ jobId: string }>('/api/v1/master/materials/sync-from-u9/async', undefined, {
      timeout: 120_000,
    }),
  getU9SyncJob: (jobId: string) =>
    api.get<U9SyncJobStatus>(`/api/v1/master/materials/sync-from-u9/jobs/${jobId}`, { timeout: 15000 }),

  /** 每料号传 code 调 cangku_yigui / cangku_shuiqi；不传 body 则同步本地全部物料 */
  syncFactoryWarehousesFromU9: (materialCodes?: string[]) =>
    api.post<FactoryWarehouseSyncResult>(
      '/api/v1/master/materials/sync-factory-warehouses-from-u9',
      materialCodes !== undefined && materialCodes.length > 0 ? materialCodes : undefined,
      { timeout: FACTORY_WAREHOUSE_SYNC_TIMEOUT_MS },
    ),

  /** 批量 lpgys；不传 body 则本地全部物料 */
  syncSuppliersFromLpgysBulk: (materialCodes?: string[]) =>
    api.post<U9LpgysBulkSyncResult>(
      '/api/v1/master/materials/sync-suppliers-from-u9',
      materialCodes !== undefined && materialCodes.length > 0 ? materialCodes : undefined,
      { timeout: FACTORY_WAREHOUSE_SYNC_TIMEOUT_MS },
    ),

  listWarehouses: (params: { page: number; size: number }) =>
    api.get<SpringPage<MaterialWarehouseRef>>('/api/v1/master/warehouses', {
      params: { page: params.page, size: params.size },
    }),
}
