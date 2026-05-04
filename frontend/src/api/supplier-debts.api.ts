import { client } from './client'
import { env } from '../env'
import type { ApiResponse, SupplierDebtOverview, SupplierPaymentMapping } from '../types'

const BASE = `${env.apiPrefix}/supplier-debts`

export async function getSupplierDebtOverview(dateFrom: string, dateTo: string): Promise<SupplierDebtOverview> {
  const res = await client.get<ApiResponse<SupplierDebtOverview>>(BASE, {
    params: { dateFrom, dateTo },
  })
  return res.data.data
}

export async function saveSupplierPaymentMapping(input: {
  provider: string
  matchText: string
  supplierKey: string
  supplierTin: string
  supplierName: string
}): Promise<SupplierPaymentMapping> {
  const res = await client.post<ApiResponse<SupplierPaymentMapping>>(`${BASE}/mappings`, input)
  return res.data.data
}

export async function deleteSupplierPaymentMapping(id: string): Promise<void> {
  await client.delete<ApiResponse<void>>(`${BASE}/mappings/${id}`)
}
