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

export type SalesAggregation = 'DAY' | 'WEEK' | 'MONTH'

export type SalesAnalysisStatus = 'MATCH' | 'SHORT' | 'OVER' | 'NO_BANK_DATA' | 'BANK_ONLY'

export interface SalesAnalysisMetric {
  current: number
  previous: number
  delta: number
  deltaPercent: number
}

export interface SalesAnalysisSummary {
  periodCount: number
  totalSales: SalesAnalysisMetric
  totalBankIncome: SalesAnalysisMetric
  totalTbcIncome: SalesAnalysisMetric
  totalBogIncome: SalesAnalysisMetric
  variance: SalesAnalysisMetric
  captureRatio: SalesAnalysisMetric
  averageSales: SalesAnalysisMetric
  averageBankIncome: SalesAnalysisMetric
}

export interface SalesAnalysisPeriodRow {
  key: string
  dateFrom: string
  dateTo: string
  sales: number
  tbcIncome: number
  bogIncome: number
  bankIncome: number
  variance: number
  variancePercent: number
  captureRatio: number
  bankMixTbc: number
  bankMixBog: number
  events: string[]
  status: SalesAnalysisStatus
}

export interface SalesAnalysisAggregationBlock {
  aggregation: SalesAggregation
  summary: SalesAnalysisSummary
  periods: SalesAnalysisPeriodRow[]
}

export interface SalesAnalysisResult {
  dateFrom: string
  dateTo: string
  generatedAt: string
  availableEvents: string[]
  day: SalesAnalysisAggregationBlock
  week: SalesAnalysisAggregationBlock
  month: SalesAnalysisAggregationBlock
}

export interface SalesProductExclusion {
  normalizedName: string
  displayName: string
  excluded: boolean
  source: string
  firstSeen: string
}

export interface SalesEvent {
  date: string
  name: string
  normalizedName: string
  createdAt: string
  updatedAt: string
}

export interface CashFlowCategory {
  category: string
  group: string
  amount: number
  transactionCount: number
}

export interface CashFlowGroup {
  group: string
  amount: number
  transactionCount: number
  categories: CashFlowCategory[]
}

export interface CashFlowMonth {
  month: string
  totalInflow: number
  totalOutflow: number
  cashInflow: number
  cashOutflow: number
  bogInflow: number
  bogOutflow: number
  tbcInflow: number
  tbcOutflow: number
  endingCash: number
  endingBog: number
  endingTbc: number
  totalBankBalance: number
  totalEndingBalance: number
  netMovement: number
  warningCount: number
  flaggedRowCount: number
  groups: CashFlowGroup[]
}

export interface CashFlowOverview {
  dateFrom: string | null
  dateTo: string | null
  availableMonths: string[]
  months: CashFlowMonth[]
  unmappedTotal: number
  unmappedCategories: CashFlowUnmappedCategory[]
}

export interface CashFlowSyncStatus {
  status: string
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  lastSuccessAt: string | null
  lastError: string | null
  rowCount: number
  refreshInProgress: boolean
}

export interface CashFlowWarning {
  month: string
  sourceRow: number
  severity: string
  code: string
  message: string
}

export interface CashFlowWarningsResponse {
  month: string | null
  total: number
  warnings: CashFlowWarning[]
}

export interface CashFlowTransaction {
  sourceRow: number
  date: string | null
  month: string
  sourceCategory: string
  category: string
  group: string
  counterparty: string
  comment: string
  materialValue: number
  serviceValue: number
  cashInflow: number
  cashOutflow: number
  cashBalance: number
  bogInflow: number
  bogOutflow: number
  bogBalance: number
  tbcInflow: number
  tbcOutflow: number
  tbcBalance: number
  validationFlag: string
  issues: string[]
}

export interface CashFlowTransactionsResponse {
  month: string
  group: string | null
  category: string | null
  transactions: CashFlowTransaction[]
}

export interface CashFlowUnmappedCategory {
  sourceCategory: string
  amount: number
  transactionCount: number
}

export interface CashFlowCategoryMapping {
  sourceCategory: string
  targetCategory: string
  source: string
}

export interface CashFlowMappingsView {
  canonicalCategories: string[]
  mappings: CashFlowCategoryMapping[]
  unmappedCategories: CashFlowUnmappedCategory[]
}

export interface CashFlowCategoryDebugMonth {
  month: string
  amount: number
  rowCount: number
}

export interface CashFlowCategoryDebugRow {
  sourceRow: number
  date: string | null
  month: string
  sourceCategory: string
  normalizedSourceCategory: string
  effectiveCategory: string
  normalizedEffectiveCategory: string
  group: string
  classificationReason: string
  countedAsIncome: boolean
  incomeAmount: number
  cashInflow: number
  bogInflow: number
  tbcInflow: number
  issues: string[]
}

export interface CashFlowCategoryDebug {
  category: string
  normalizedCategory: string
  dateFrom: string | null
  dateTo: string | null
  totalAmount: number
  includedRowCount: number
  excludedRowCount: number
  months: CashFlowCategoryDebugMonth[]
  rows: CashFlowCategoryDebugRow[]
}
