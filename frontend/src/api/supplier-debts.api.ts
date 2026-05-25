import { client, unwrapData } from './client'
import { env } from '../env'
import type {
  ApiResponse,
  SupplierCashPayment,
  SupplierCashPaymentInput,
  SupplierDebtAudit,
  SupplierDebtOverview,
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

export async function auditSupplierDebts(dateFrom?: string, dateTo?: string): Promise<SupplierDebtAudit> {
  const res = await client.post<ApiResponse<SupplierDebtAudit>>(`${BASE}/audit-random`, null, {
    params: dateParams(dateFrom, dateTo),
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
