import { client, unwrapData } from './client'
import { env } from '../env'
import type {
  ApiResponse,
  SupplierCashPayment,
  SupplierCashPaymentInput,
  SupplierCreditorOverview,
  SupplierCreditorRow,
  SupplierDebtOverview,
  SupplierDebtRawPayloads,
  SupplierDebtRow,
  SupplierPaymentMapping,
} from '../types'

const BASE = `${env.apiPrefix}/supplier-debts`

function dateParams(dateFrom?: string, dateTo?: string, refreshSources?: boolean) {
  return {
    ...(dateFrom ? { dateFrom } : {}),
    ...(dateTo ? { dateTo } : {}),
    ...(refreshSources ? { refreshSources: true } : {}),
  }
}

export async function getSupplierCreditors(dateFrom?: string, dateTo?: string): Promise<SupplierCreditorOverview> {
  const res = await client.get<ApiResponse<SupplierCreditorOverview>>(`${BASE}/creditors`, {
    params: dateParams(dateFrom, dateTo),
  })
  return unwrapData(res)
}

export async function syncAllSupplierCreditors(
  dateFrom?: string,
  dateTo?: string,
): Promise<SupplierCreditorOverview> {
  const res = await client.post<ApiResponse<SupplierCreditorOverview>>(`${BASE}/creditors/sync-all`, null, {
    params: dateParams(dateFrom, dateTo),
  })
  return unwrapData(res)
}

export async function syncSupplierCreditor(
  supplierKey: string,
  dateFrom?: string,
  dateTo?: string,
): Promise<SupplierCreditorRow> {
  const res = await client.post<ApiResponse<SupplierCreditorRow>>(`${BASE}/creditors/${encodeURIComponent(supplierKey)}/sync`, null, {
    params: dateParams(dateFrom, dateTo),
  })
  return unwrapData(res)
}

export async function setSupplierCreditorActive(
  supplierKey: string,
  active: boolean,
  dateFrom?: string,
  dateTo?: string,
): Promise<SupplierCreditorOverview> {
  const res = await client.patch<ApiResponse<SupplierCreditorOverview>>(
    `${BASE}/creditors/${encodeURIComponent(supplierKey)}/active`,
    { active },
    { params: dateParams(dateFrom, dateTo) },
  )
  return unwrapData(res)
}
export async function getSupplierDebtOverview(
  dateFrom?: string,
  dateTo?: string,
  refreshSources = false,
): Promise<SupplierDebtOverview> {
  const res = await client.get<ApiResponse<SupplierDebtOverview>>(BASE, {
    params: dateParams(dateFrom, dateTo, refreshSources),
  })
  return unwrapData(res)
}

export async function startSupplierDebtRefresh(
  dateFrom?: string,
  dateTo?: string,
): Promise<SupplierDebtOverview> {
  const res = await client.post<ApiResponse<SupplierDebtOverview>>(`${BASE}/refresh`, null, {
    params: dateParams(dateFrom, dateTo),
  })
  return unwrapData(res)
}

export async function getSupplierDebtSupplierTransactions(
  supplierKey: string,
  dateFrom?: string,
  dateTo?: string,
  refreshSources = false,
): Promise<SupplierDebtRow> {
  const res = await client.get<ApiResponse<SupplierDebtRow>>(`${BASE}/suppliers/${encodeURIComponent(supplierKey)}/transactions`, {
    params: dateParams(dateFrom, dateTo, refreshSources),
  })
  return unwrapData(res)
}

export async function getSupplierDebtRawPayloads(
  dateFrom?: string,
  dateTo?: string,
  refreshSources = false,
): Promise<SupplierDebtRawPayloads> {
  const res = await client.get<ApiResponse<SupplierDebtRawPayloads>>(`${BASE}/debug/raw-payloads`, {
    params: dateParams(dateFrom, dateTo, refreshSources),
  })
  return unwrapData(res)
}

export async function saveSupplierPaymentMapping(input: {
  provider: string
  matchText: string
  supplierKey: string
  supplierTin: string
  supplierName: string
}): Promise<SupplierPaymentMapping> {
  const res = await client.post<ApiResponse<SupplierPaymentMapping>>(`${BASE}/mappings`, input)
  return unwrapData(res)
}

export async function deleteSupplierPaymentMapping(id: string): Promise<void> {
  await client.delete<ApiResponse<void>>(`${BASE}/mappings/${id}`)
}

export async function listSupplierCashPayments(dateFrom?: string, dateTo?: string): Promise<SupplierCashPayment[]> {
  const res = await client.get<ApiResponse<SupplierCashPayment[]>>(`${BASE}/cash-payments`, {
    params: dateParams(dateFrom, dateTo),
  })
  return unwrapData(res)
}

export async function saveSupplierCashPayment(input: SupplierCashPaymentInput): Promise<SupplierCashPayment> {
  const res = await client.post<ApiResponse<SupplierCashPayment>>(`${BASE}/cash-payments`, input)
  return unwrapData(res)
}

export async function deleteSupplierCashPayment(id: string): Promise<void> {
  await client.delete<ApiResponse<void>>(`${BASE}/cash-payments/${id}`)
}
