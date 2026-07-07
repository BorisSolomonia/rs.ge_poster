import { client, unwrapData } from './client'
import { env } from '../env'
import type {
  ApiResponse,
  CashFlowCategory,
  CashFlowDirection,
  CashFlowDrilldown,
  CashFlowMatchType,
  CashFlowMatrix,
  CashFlowRule,
  CashFlowStatus,
} from '../types'

const BASE = `${env.apiPrefix}/cash-flow`

function dateParams(from?: string, to?: string) {
  return {
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
  }
}

export async function getCashFlowMatrix(from?: string, to?: string): Promise<CashFlowMatrix> {
  const res = await client.get<ApiResponse<CashFlowMatrix>>(`${BASE}/matrix`, { params: dateParams(from, to) })
  return unwrapData(res)
}

export async function getCashFlowTransactions(
  categoryId: string,
  month?: string | null,
  from?: string,
  to?: string,
): Promise<CashFlowDrilldown> {
  const res = await client.get<ApiResponse<CashFlowDrilldown>>(`${BASE}/transactions`, {
    params: { categoryId, ...(month ? { month } : {}), ...dateParams(from, to) },
  })
  return unwrapData(res)
}

export type CategorizeScope = 'SINGLE' | 'CASCADE'

export async function categorizeTransaction(body: {
  fingerprint: string
  categoryId: string
  scope: CategorizeScope
  counterpartyInn?: string
  counterpartyAccount?: string
  counterparty?: string
}): Promise<void> {
  const res = await client.post<ApiResponse<void>>(`${BASE}/transactions/categorize`, body)
  unwrapData(res)
}

export async function refreshCashFlow(from?: string, to?: string): Promise<CashFlowStatus> {
  const res = await client.post<ApiResponse<CashFlowStatus>>(`${BASE}/refresh`, null, { params: dateParams(from, to) })
  return unwrapData(res)
}

export async function getCashFlowStatus(): Promise<CashFlowStatus> {
  const res = await client.get<ApiResponse<CashFlowStatus>>(`${BASE}/status`)
  return unwrapData(res)
}

export async function getCashFlowCategories(): Promise<CashFlowCategory[]> {
  const res = await client.get<ApiResponse<CashFlowCategory[]>>(`${BASE}/categories`)
  return unwrapData(res)
}

export async function createCashFlowCategory(body: {
  section?: string
  direction?: CashFlowDirection
  nameKa: string
  parentId?: string
  order?: number
}): Promise<CashFlowCategory> {
  const res = await client.post<ApiResponse<CashFlowCategory>>(`${BASE}/categories`, body)
  return unwrapData(res)
}

export async function updateCashFlowCategory(
  id: string,
  body: { section?: string; direction?: CashFlowDirection; nameKa?: string; order?: number },
): Promise<CashFlowCategory> {
  const res = await client.put<ApiResponse<CashFlowCategory>>(`${BASE}/categories/${encodeURIComponent(id)}`, body)
  return unwrapData(res)
}

export async function deleteCashFlowCategory(id: string): Promise<void> {
  const res = await client.delete<ApiResponse<void>>(`${BASE}/categories/${encodeURIComponent(id)}`)
  unwrapData(res)
}

export async function getCashFlowRules(): Promise<CashFlowRule[]> {
  const res = await client.get<ApiResponse<CashFlowRule[]>>(`${BASE}/rules`)
  return unwrapData(res)
}

export async function upsertCashFlowRule(body: {
  matchType: CashFlowMatchType
  matchValue: string
  direction?: CashFlowDirection | null
  categoryId: string
}): Promise<CashFlowRule> {
  const res = await client.post<ApiResponse<CashFlowRule>>(`${BASE}/rules`, body)
  return unwrapData(res)
}

export async function deleteCashFlowRule(id: string): Promise<void> {
  const res = await client.delete<ApiResponse<void>>(`${BASE}/rules/${encodeURIComponent(id)}`)
  unwrapData(res)
}
