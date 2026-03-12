import { client } from './client'
import { env } from '../env'
import type { ApiResponse, SalesEvent } from '../types'

const BASE = `${env.apiPrefix}/sales-events`

export async function listSalesEvents(): Promise<SalesEvent[]> {
  const res = await client.get<ApiResponse<SalesEvent[]>>(BASE)
  return res.data.data
}

export async function suggestSalesEvents(query: string): Promise<string[]> {
  if (!query.trim()) return []
  const res = await client.get<ApiResponse<string[]>>(`${BASE}/suggest`, { params: { query } })
  return res.data.data
}

export async function upsertSalesEvent(date: string, name: string): Promise<SalesEvent> {
  const res = await client.post<ApiResponse<SalesEvent>>(BASE, { date, name })
  return res.data.data
}

export async function deleteSalesEvent(date: string): Promise<void> {
  await client.delete(BASE, { params: { date } })
}
