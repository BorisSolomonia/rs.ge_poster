import { client } from './client'
import { env } from '../env'
import type {
  ApiResponse,
  ProductMapping,
  CreateProductMappingRequest,
  PatternTestRequest,
  PatternTestResult,
} from '../types'

const BASE = `${env.apiPrefix}/product-mappings`

export async function getProductMappings(supplierMappingId: string): Promise<ProductMapping[]> {
  const res = await client.get<ApiResponse<ProductMapping[]>>(BASE, {
    params: { supplierMappingId },
  })
  return res.data.data
}

export async function createProductMapping(req: CreateProductMappingRequest): Promise<ProductMapping> {
  const res = await client.post<ApiResponse<ProductMapping>>(BASE, req)
  return res.data.data
}

export async function updateProductMapping(
  id: string,
  req: Partial<CreateProductMappingRequest>
): Promise<ProductMapping> {
  const res = await client.put<ApiResponse<ProductMapping>>(`${BASE}/${id}`, req)
  return res.data.data
}

export async function deleteProductMapping(id: string): Promise<void> {
  await client.delete(`${BASE}/${id}`)
}

export async function testPattern(req: PatternTestRequest): Promise<PatternTestResult> {
  const res = await client.post<ApiResponse<PatternTestResult>>(`${BASE}/test-match`, req)
  return res.data.data
}
