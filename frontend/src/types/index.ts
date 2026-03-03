// ─── Config models ────────────────────────────────────────────────────────────

export interface SupplierMapping {
  id: string
  posterAlias: string
  rsgeRawValue: string
  rsgeTaxId: string | null
  rsgeOfficialName: string
  posterExcluded: boolean
  rsgeExcluded: boolean
  createdAt: string
}

export interface ProductMapping {
  id: string
  supplierMappingId: string
  rsgeProductPattern: string
  posterProductPattern: string
  isRegex: boolean
  isExcluded: boolean
  priority: number
  createdAt: string
}

export interface StandaloneSupplier {
  platform: 'RSGE' | 'POSTER'
  name: string
  isExcluded: boolean
  firstSeen: string
}

// ─── Reconciliation ───────────────────────────────────────────────────────────

export type ReconciliationStatus = 'MATCH' | 'DISCREPANCY' | 'MISSING_IN_POSTER' | 'MISSING_IN_RSGE'

export interface ReconciliationLineResult {
  posterAlias: string | null
  rsgeOfficialName: string | null
  rsgeRawValue: string | null
  rsgeTotal: number
  posterTotal: number
  diff: number
  status: ReconciliationStatus
  rsgeProducts: string[]
  posterProductsRaw: string[]
  waybillNumbers: string[]
  posterDocNumbers: number[]
  correctionAction: string | null
}

export interface ReconciliationSummary {
  totalLines: number
  matched: number
  discrepancy: number
  missingPoster: number
  missingRsge: number
}

export interface NewSuppliersDiscovered {
  rsge: string[]
  poster: string[]
}

export interface ReconciliationResult {
  runId: string
  dateFrom: string
  dateTo: string
  generatedAt: string
  expiresAt: string
  summary: ReconciliationSummary
  lines: ReconciliationLineResult[]
  newSuppliersDiscovered: NewSuppliersDiscovered
}

export interface ReconciliationResultSummary {
  runId: string
  dateFrom: string
  dateTo: string
  generatedAt: string
  expiresAt: string
  summary: ReconciliationSummary
}

// ─── Status view ──────────────────────────────────────────────────────────────

export interface SupplierMappingStatusView {
  mapped: SupplierMapping[]
  unmappedPoster: StandaloneSupplier[]
  unmappedRsge: StandaloneSupplier[]
}

// ─── API wrapper ──────────────────────────────────────────────────────────────

export interface ApiResponse<T> {
  success: boolean
  data: T
  error: string | null
}

// ─── Request DTOs ─────────────────────────────────────────────────────────────

export interface CreateSupplierMappingRequest {
  posterAlias: string
  rsgeRawValue: string
}

export interface UpdateSupplierMappingRequest {
  posterAlias?: string
  rsgeRawValue?: string
  posterExcluded?: boolean
  rsgeExcluded?: boolean
}

export interface CreateProductMappingRequest {
  supplierMappingId: string
  rsgeProductPattern: string
  posterProductPattern: string
  isRegex: boolean
  isExcluded: boolean
  priority: number
}

export interface PatternTestRequest {
  pattern: string
  testValue: string
  isRegex: boolean
}

export interface PatternTestResult {
  matches: boolean
  pattern: string
  testValue: string
  isRegex: boolean
  error: string | null
}
