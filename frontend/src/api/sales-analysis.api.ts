import { client } from './client'
import { env } from '../env'
import type { ApiResponse, SalesAnalysisResult } from '../types'

const BASE = `${env.apiPrefix}/sales-analysis`

export async function runSalesAnalysis(
  salesFile: File,
  dateFrom: string,
  dateTo: string
): Promise<SalesAnalysisResult> {
  // Bank income (TBC/BOG) is gathered from the cash-flow backend; only the Poster
  // sales file is uploaded.
  const formData = new FormData()
  formData.append('salesFile', salesFile)
  formData.append('dateFrom', dateFrom)
  formData.append('dateTo', dateTo)

  const res = await client.post<ApiResponse<SalesAnalysisResult>>(`${BASE}/analyze`, formData)
  if (!res.data.success) throw new Error(res.data.error || 'Sales analysis failed')
  return res.data.data
}
