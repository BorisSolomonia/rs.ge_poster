import { client } from './client'
import { env } from '../env'
import type { ApiResponse, SalesProductExclusion } from '../types'

const BASE = `${env.apiPrefix}/sales-products`

export async function listSalesProducts(search = ''): Promise<SalesProductExclusion[]> {
  const res = await client.get<ApiResponse<SalesProductExclusion[]>>(BASE, {
    params: search ? { search } : undefined,
  })
  return res.data.data
}

export async function createSalesProduct(displayName: string, excluded: boolean): Promise<SalesProductExclusion> {
  const res = await client.post<ApiResponse<SalesProductExclusion>>(BASE, { displayName, excluded })
  return res.data.data
}

export async function setSalesProductExcluded(displayName: string, excluded: boolean): Promise<void> {
  await client.patch(`${BASE}/exclude`, { displayName, excluded })
}
