import { api } from './http'

export type PortalContractSummary = {
  id: number
  contractNo: string
  title: string
  status: string
  contractType: string
  supplierId: number
  supplierCode: string
  supplierName: string
  procurementOrgId: number
  procurementOrgCode: string
  startDate: string | null
  endDate: string | null
  totalAmount: string | null
  currency: string
}

export type PortalContractLine = {
  id: number
  lineNo: number
  materialId: number | null
  materialCode: string | null
  materialName: string | null
  materialDesc: string | null
  qty: string | null
  uom: string | null
  unitPrice: string | null
  amount: string | null
  remark: string | null
}

export type PortalContractDetail = PortalContractSummary & {
  remark: string | null
  lines: PortalContractLine[]
}

export const portalContractApi = {
  list: () => api.get<PortalContractSummary[]>('/api/v1/portal/contracts'),
  get: (id: number) => api.get<PortalContractDetail>(`/api/v1/portal/contracts/${id}`),
}
