import { client } from './client'
import { env } from '../env'
import type { ApiResponse, ReconciliationResult, ReconciliationResultSummary } from '../types'

const BASE = `${env.apiPrefix}/reconciliation`

export async function runAnalysis(
  rsgeFile: File,
  posterFile: File,
  dateFrom: string,
  dateTo: string
): Promise<ReconciliationResult> {
  const formData = new FormData()
  formData.append('rsgeFile', rsgeFile)
  formData.append('posterFile', posterFile)
  formData.append('dateFrom', dateFrom)
  formData.append('dateTo', dateTo)

  const res = await client.post<ApiResponse<ReconciliationResult>>(
    `${BASE}/analyze`,
    formData
  )
  if (!res.data.success) throw new Error(res.data.error || 'Analysis failed')
  return res.data.data
}

export async function runPurchaseAnalysis(
  posterFile: File,
  dateFrom: string,
  dateTo: string
): Promise<ReconciliationResult> {
  const formData = new FormData()
  formData.append('posterFile', posterFile)
  formData.append('dateFrom', dateFrom)
  formData.append('dateTo', dateTo)

  const res = await client.post<ApiResponse<ReconciliationResult>>(
    `${BASE}/purchase-analyze`,
    formData
  )
  if (!res.data.success) throw new Error(res.data.error || 'Purchase analysis failed')
  return res.data.data
}

export async function listResults(): Promise<ReconciliationResultSummary[]> {
  const res = await client.get<ApiResponse<ReconciliationResultSummary[]>>(
    `${BASE}/results`
  )
  return res.data.data
}

export async function getResult(runId: string): Promise<ReconciliationResult> {
  const res = await client.get<ApiResponse<ReconciliationResult>>(
    `${BASE}/results/${runId}`
  )
  return res.data.data
}
