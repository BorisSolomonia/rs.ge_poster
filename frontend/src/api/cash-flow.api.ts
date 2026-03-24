import { client } from './client'
import { env } from '../env'
import type {
  ApiResponse,
  CashFlowGroup,
  CashFlowOverview,
  CashFlowSyncStatus,
  CashFlowTransactionsResponse,
  CashFlowWarningsResponse,
} from '../types'

const BASE = `${env.apiPrefix}/cash-flow`

export async function getCashFlowStatus(): Promise<CashFlowSyncStatus> {
  const res = await client.get<ApiResponse<CashFlowSyncStatus>>(`${BASE}/status`)
  return res.data.data
}

export async function refreshCashFlow(): Promise<CashFlowSyncStatus> {
  const res = await client.post<ApiResponse<CashFlowSyncStatus>>(`${BASE}/refresh`)
  return res.data.data
}

export async function getCashFlowOverview(from?: string, to?: string): Promise<CashFlowOverview> {
  const res = await client.get<ApiResponse<CashFlowOverview>>(`${BASE}/overview`, {
    params: {
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
    },
  })
  return res.data.data
}

export async function getCashFlowCategories(month: string): Promise<CashFlowGroup[]> {
  const res = await client.get<ApiResponse<CashFlowGroup[]>>(`${BASE}/categories`, { params: { month } })
  return res.data.data
}

export async function getCashFlowTransactions(month: string, group?: string, category?: string): Promise<CashFlowTransactionsResponse> {
  const res = await client.get<ApiResponse<CashFlowTransactionsResponse>>(`${BASE}/transactions`, {
    params: {
      month,
      ...(group ? { group } : {}),
      ...(category ? { category } : {}),
    },
  })
  return res.data.data
}

export async function getCashFlowWarnings(month?: string): Promise<CashFlowWarningsResponse> {
  const res = await client.get<ApiResponse<CashFlowWarningsResponse>>(`${BASE}/warnings`, {
    params: month ? { month } : undefined,
  })
  return res.data.data
}
