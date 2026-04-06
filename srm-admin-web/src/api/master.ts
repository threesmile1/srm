import { api } from './http'

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
}
