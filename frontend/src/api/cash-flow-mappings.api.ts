import { client } from './client'
import { env } from '../env'
import type { ApiResponse, CashFlowCategoryMapping, CashFlowMappingsView } from '../types'

const BASE = `${env.apiPrefix}/cash-flow-mappings`

export async function getCashFlowMappings(from?: string, to?: string): Promise<CashFlowMappingsView> {
  const res = await client.get<ApiResponse<CashFlowMappingsView>>(BASE, {
    params: {
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
    },
  })
  return res.data.data
}

export async function upsertCashFlowMapping(sourceCategory: string, targetCategory: string): Promise<CashFlowCategoryMapping> {
  const res = await client.post<ApiResponse<CashFlowCategoryMapping>>(BASE, { sourceCategory, targetCategory })
  return res.data.data
}

export async function deleteCashFlowMapping(sourceCategory: string): Promise<void> {
  await client.delete(BASE, { params: { sourceCategory } })
}
