import { client } from './client'
import { env } from '../env'
import type { ApiResponse, SalesAnalysisResult } from '../types'

const BASE = `${env.apiPrefix}/sales-analysis`

export async function runSalesAnalysis(
  salesFile: File,
  tbcFile: File,
  bogFile: File,
  dateFrom: string,
  dateTo: string
): Promise<SalesAnalysisResult> {
  const formData = new FormData()
  formData.append('salesFile', salesFile)
  formData.append('tbcFile', tbcFile)
  formData.append('bogFile', bogFile)
  formData.append('dateFrom', dateFrom)
  formData.append('dateTo', dateTo)

  const res = await client.post<ApiResponse<SalesAnalysisResult>>(`${BASE}/analyze`, formData)
  if (!res.data.success) throw new Error(res.data.error || 'Sales analysis failed')
  return res.data.data
}
