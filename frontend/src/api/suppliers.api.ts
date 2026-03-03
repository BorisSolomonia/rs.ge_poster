import { client } from './client'
import { env } from '../env'
import type {
  ApiResponse,
  SupplierMapping,
  SupplierMappingStatusView,
  StandaloneSupplier,
  CreateSupplierMappingRequest,
  UpdateSupplierMappingRequest,
} from '../types'

const BASE = `${env.apiPrefix}/supplier-mappings`

export async function getAll(): Promise<SupplierMapping[]> {
  const res = await client.get<ApiResponse<SupplierMapping[]>>(BASE)
  return res.data.data
}

export async function getStatus(): Promise<SupplierMappingStatusView> {
  const res = await client.get<ApiResponse<SupplierMappingStatusView>>(`${BASE}/status`)
  return res.data.data
}

export async function getUnmapped(): Promise<StandaloneSupplier[]> {
  const res = await client.get<ApiResponse<StandaloneSupplier[]>>(`${BASE}/unmapped`)
  return res.data.data
}

export async function createMapping(req: CreateSupplierMappingRequest): Promise<SupplierMapping> {
  const res = await client.post<ApiResponse<SupplierMapping>>(BASE, req)
  return res.data.data
}

export async function updateMapping(id: string, req: UpdateSupplierMappingRequest): Promise<SupplierMapping> {
  const res = await client.put<ApiResponse<SupplierMapping>>(`${BASE}/${id}`, req)
  return res.data.data
}

export async function deleteMapping(id: string): Promise<void> {
  await client.delete(`${BASE}/${id}`)
}

export async function togglePosterExcluded(id: string): Promise<SupplierMapping> {
  const res = await client.patch<ApiResponse<SupplierMapping>>(`${BASE}/${id}/exclude-poster`)
  return res.data.data
}

export async function toggleRsgeExcluded(id: string): Promise<SupplierMapping> {
  const res = await client.patch<ApiResponse<SupplierMapping>>(`${BASE}/${id}/exclude-rsge`)
  return res.data.data
}

export async function excludeStandalone(platform: string, name: string): Promise<void> {
  await client.patch(`${BASE}/standalone/exclude`, { platform, name })
}
