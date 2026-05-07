import { client, unwrapData } from './client'
import { env } from '../env'
import type { ApiResponse, BankAnalysisOverview, BankDirection, BankTransactionMapping, TbcPasswordChangeResult } from '../types'

const BASE = `${env.apiPrefix}/bank-analysis`

export async function getTbcBankAnalysis(dateFrom: string, dateTo: string): Promise<BankAnalysisOverview> {
  const res = await client.get<ApiResponse<BankAnalysisOverview>>(`${BASE}/tbc`, {
    params: { dateFrom, dateTo },
  })
  return unwrapData(res)
}

export async function getBogBankAnalysis(dateFrom: string, dateTo: string): Promise<BankAnalysisOverview> {
  const res = await client.get<ApiResponse<BankAnalysisOverview>>(`${BASE}/bog`, {
    params: { dateFrom, dateTo },
  })
  return unwrapData(res)
}

export async function changeTbcPassword(otp: string, newPassword: string, currentPasswordOverride?: string): Promise<TbcPasswordChangeResult> {
  const res = await client.post<ApiResponse<TbcPasswordChangeResult>>(`${BASE}/tbc/password-change`, {
    otp,
    newPassword,
    currentPasswordOverride,
  })
  return unwrapData(res)
}

export async function listBankMappings(): Promise<BankTransactionMapping[]> {
  const res = await client.get<ApiResponse<BankTransactionMapping[]>>(`${BASE}/mappings`)
  return unwrapData(res)
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
  return unwrapData(res)
}

export async function deleteBankMapping(id: string): Promise<void> {
  await client.delete<ApiResponse<void>>(`${BASE}/mappings/${id}`)
}
