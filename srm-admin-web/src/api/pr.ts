import { api } from './http'

export type PrSummary = {
  id: number
  prNo: string
  status: string
  requesterName: string | null
  department: string | null
  procurementOrgCode: string
}

export type PrLine = {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  qty: string
  uom: string
  unitPrice: string | null
  requestedDate: string | null
  warehouseId: number | null
  warehouseCode: string | null
  supplierId: number | null
  supplierCode: string | null
  remark: string | null
  convertedPoId: number | null
  convertedPoNo: string | null
}

export type PrDetail = PrSummary & {
  procurementOrgId: number
  remark: string | null
  lines: PrLine[]
}

export const prApi = {
  list: (procurementOrgId: number) =>
    api.get<PrSummary[]>('/api/v1/purchase-requisitions', { params: { procurementOrgId } }),
  get: (id: number) => api.get<PrDetail>(`/api/v1/purchase-requisitions/${id}`),
  create: (body: {
    procurementOrgId: number
    requesterName?: string
    department?: string
    remark?: string
    lines: {
      materialId: number
      warehouseId?: number | null
      supplierId?: number | null
      qty: number
      uom?: string
      unitPrice?: number | null
      requestedDate?: string | null
      remark?: string
    }[]
  }) => api.post<PrDetail>('/api/v1/purchase-requisitions', body),
  submit: (id: number) => api.post<PrDetail>(`/api/v1/purchase-requisitions/${id}/submit`),
  approve: (id: number) => api.post<PrDetail>(`/api/v1/purchase-requisitions/${id}/approve`),
  reject: (id: number, reason?: string) =>
    api.post<PrDetail>(`/api/v1/purchase-requisitions/${id}/reject`, { reason }),
  cancel: (id: number) => api.post<PrDetail>(`/api/v1/purchase-requisitions/${id}/cancel`),
  /** 转 PO：由采购填写每行供应商、采购单价、约定交期（未填则后端用请购交期） */
  convertToPo: (
    id: number,
    lines: { lineId: number; supplierId: number; unitPrice: number; requestedDate?: string | null }[],
  ) => api.post<{ poId: number; poNo: string }[]>(`/api/v1/purchase-requisitions/${id}/convert-to-po`, { lines }),
}
