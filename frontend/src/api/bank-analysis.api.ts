import { client } from './client'
import { env } from '../env'
import type { ApiResponse, BankAnalysisOverview, BankDirection, BankTransactionMapping } from '../types'

const BASE = `${env.apiPrefix}/bank-analysis`

export async function getTbcBankAnalysis(dateFrom: string, dateTo: string): Promise<BankAnalysisOverview> {
  const res = await client.get<ApiResponse<BankAnalysisOverview>>(`${BASE}/tbc`, {
    params: { dateFrom, dateTo },
  })
  return res.data.data
}

export async function getBogBankAnalysis(dateFrom: string, dateTo: string): Promise<BankAnalysisOverview> {
  const res = await client.get<ApiResponse<BankAnalysisOverview>>(`${BASE}/bog`, {
    params: { dateFrom, dateTo },
  })
  return res.data.data
}

export async function listBankMappings(): Promise<BankTransactionMapping[]> {
  const res = await client.get<ApiResponse<BankTransactionMapping[]>>(`${BASE}/mappings`)
  return res.data.data
}

export async function saveBankMapping(
  direction: BankDirection,
  matchText: string,
  category: string,
): Promise<BankTransactionMapping> {
  const res = await client.post<ApiResponse<BankTransactionMapping>>(`${BASE}/mappings`, {
    direction,
    matchText,
    category,
  })
  return res.data.data
}

export async function deleteBankMapping(id: string): Promise<void> {
  await client.delete<ApiResponse<void>>(`${BASE}/mappings/${id}`)
}
