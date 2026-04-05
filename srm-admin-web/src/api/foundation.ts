import { api } from './http'

export type Ledger = { id: number; code: string; name: string; u9LedgerCode: string | null }
export type OrgUnit = {
  id: number
  ledgerId: number
  orgType: string
  code: string
  name: string
  u9OrgCode: string | null
}
export type Warehouse = {
  id: number
  procurementOrgId: number
  code: string
  name: string
  u9WhCode: string | null
}

export const foundationApi = {
  listLedgers: () => api.get<Ledger[]>('/api/v1/ledgers'),
  listOrgUnits: (ledgerId: number) => api.get<OrgUnit[]>(`/api/v1/ledgers/${ledgerId}/org-units`),
  listWarehouses: (orgId: number) => api.get<Warehouse[]>(`/api/v1/org-units/${orgId}/warehouses`),
}
