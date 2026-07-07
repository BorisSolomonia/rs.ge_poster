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
  regex: boolean
  excluded: boolean
  priority: number
  createdAt: string
}

export interface StandaloneSupplier {
  platform: 'RSGE' | 'POSTER'
  name: string
  excluded: boolean
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
  skippedRsgeRows?: number
  skippedPosterRows?: number
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
  code?: string | null
  technicalDetails?: string | null
  timestamp?: string
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
  regex: boolean
  excluded: boolean
  priority: number
}

export interface UpdateProductMappingRequest {
  rsgeProductPattern?: string
  posterProductPattern?: string
  isRegex?: boolean
  isExcluded?: boolean
  priority?: number
}

export interface PatternTestRequest {
  pattern: string
  testValue: string
  regex: boolean
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

export interface SalesAnalysisProductPoint {
  key: string
  dateFrom: string
  dateTo: string
  grossRevenue: number
  quantity: number
  profit: number
  profitPercentage: number
  events: string[]
}

export interface SalesAnalysisProductOption {
  productKey: string
  productName: string
  grossRevenueTotal: number
}

export interface SalesAnalysisProductSeries {
  productKey: string
  productName: string
  periods: SalesAnalysisProductPoint[]
}

export interface SalesAnalysisAggregationBlock {
  aggregation: SalesAggregation
  summary: SalesAnalysisSummary
  periods: SalesAnalysisPeriodRow[]
  availableProducts: SalesAnalysisProductOption[]
  productSeries: SalesAnalysisProductSeries[]
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

// ─── Cash flow (bank-driven Agicap-style matrix) ────────────────────────────

export type CashFlowSectionKey = 'OPERATING' | 'INVESTING' | 'FINANCING'
export type CashFlowDirection = 'INFLOW' | 'OUTFLOW'
export type CashFlowMatchType = 'TAX_ID' | 'IBAN' | 'NAME'
export type CashFlowResolvedBy = 'OVERRIDE' | 'RULE_TAX_ID' | 'RULE_IBAN' | 'RULE_NAME' | 'UNCATEGORIZED'

export interface CashFlowMatrixCategory {
  categoryId: string
  nameKa: string
  total: number
  monthly: Record<string, number>
  transactionCount: number
  children: CashFlowMatrixCategory[]
}

export interface CashFlowMatrixDirection {
  direction: CashFlowDirection
  nameKa: string
  total: number
  monthly: Record<string, number>
  categories: CashFlowMatrixCategory[]
}

export interface CashFlowMatrixSection {
  sectionKey: CashFlowSectionKey
  nameKa: string
  total: number
  monthly: Record<string, number>
  directions: CashFlowMatrixDirection[]
}

export interface CashFlowMatrix {
  from: string
  to: string
  months: string[]
  sections: CashFlowMatrixSection[]
  generatedAt: string
}

export interface CashFlowTransaction {
  fingerprint: string
  source: string
  date: string
  monthKey: string
  direction: string
  amount: number
  currency: string
  counterparty: string
  counterpartyInn: string
  counterpartyAccount: string
  description: string
  reference: string
  categoryId: string
  categoryNameKa: string
  resolvedBy: CashFlowResolvedBy
}

export interface CashFlowDrilldown {
  categoryId: string
  categoryNameKa: string
  month: string | null
  from: string
  to: string
  total: number
  transactions: CashFlowTransaction[]
}

export interface CashFlowCategory {
  id: string
  code: string
  sectionKey: CashFlowSectionKey
  sectionNameKa: string
  direction: CashFlowDirection
  directionNameKa: string
  nameKa: string
  parentId: string | null
  hasChildren: boolean
  order: number
  builtin: boolean
}

export interface CashFlowRule {
  id: string
  matchType: CashFlowMatchType
  matchValue: string
  direction: CashFlowDirection | null
  categoryId: string
  categoryNameKa: string
  source: string
  updatedAt: string
}

export interface CashFlowStatus {
  bogConfigured: boolean
  tbcConfigured: boolean
  lastRefreshAt: string | null
  lastRefreshError: string
}

export type BankDirection = 'CREDIT' | 'DEBIT' | 'BOTH'

export interface BankTransactionMapping {
  id: string
  direction: BankDirection
  matchText: string
  normalizedMatchText: string
  category: string
  normalizedCategory: string
  source: string
  createdAt: string
  updatedAt: string
}

export interface BankCategoryTotal {
  direction: BankDirection
  category: string
  amount: number
  transactionCount: number
}

export interface BankUnmappedGroup {
  direction: BankDirection
  matchText: string
  counterparty: string
  description: string
  amount: number
  transactionCount: number
  largestTransaction: number
}

export interface BankTransaction {
  date: string
  direction: BankDirection
  amount: number
  currency: string
  accountNumber: string
  counterparty: string
  description: string
  reference: string
  category: string
  mapped: boolean
  mappingMatchText: string
}

export interface BankAnalysisOverview {
  dateFrom: string
  dateTo: string
  provider: string
  accountNumber: string
  currency: string
  totalCredits: number
  totalDebits: number
  netMovement: number
  transactionCount: number
  categoryTotals: BankCategoryTotal[]
  largeUnmappedCredits: BankUnmappedGroup[]
  unmappedDebitReceivers: BankUnmappedGroup[]
  mappings: BankTransactionMapping[]
  transactions: BankTransaction[]
}

export interface TbcPasswordChangeResult {
  message: string
  code: string
  secretManagerUpdateRequired: boolean
}

export interface SupplierPaymentMapping {
  id: string
  provider: string
  matchText: string
  normalizedMatchText: string
  supplierKey: string
  supplierTin: string
  supplierName: string
  source: string
  createdAt: string
  updatedAt: string
}

export interface SupplierDebtPurchase {
  waybillNumber: string
  date: string | null
  amount: number
  supplierTin: string
  supplierName: string
}

export interface SupplierDebtPayment {
  id: string
  date: string | null
  amount: number
  provider: string
  counterparty: string
  counterpartyInn: string
  counterpartyAccount: string
  description: string
  reference: string
  matchReason: string
}

export interface SupplierDebtRow {
  supplierKey: string
  supplierTin: string
  supplierName: string
  purchaseTotal: number
  purchaseCount: number
  bogPaidTotal: number
  bogPaymentCount: number
  tbcPaidTotal: number
  tbcPaymentCount: number
  cashPaidTotal: number
  cashPaymentCount: number
  paidTotal: number
  paymentCount: number
  debtLeft: number
  lastPurchaseDate: string | null
  lastPaymentDate: string | null
  newFromRsge: boolean
  purchases: SupplierDebtPurchase[]
  payments: SupplierDebtPayment[]
}

export interface SupplierCreditorRow {
  supplierKey: string
  supplierTin: string
  supplierName: string
  active: boolean
  synced: boolean
  lastSyncedAt: string | null
  lastSyncError: string
  purchaseTotal: number
  purchaseCount: number
  bogPaidTotal: number
  bogPaymentCount: number
  tbcPaidTotal: number
  tbcPaymentCount: number
  cashPaidTotal: number
  cashPaymentCount: number
  paidTotal: number
  paymentCount: number
  debtLeft: number
  lastPurchaseDate: string | null
  lastPaymentDate: string | null
  purchases: SupplierDebtPurchase[]
  payments: SupplierDebtPayment[]
}

export interface SupplierCreditorOverview {
  dateFrom: string
  dateTo: string
  approximateDebtTotal: number
  syncedSupplierCount: number
  totalSupplierCount: number
  generatedAt: string
  suppliers: SupplierCreditorRow[]
}
export interface SupplierDebtSourceStatus {
  source: string
  status: string
  message: string
  technicalDetails: string
  recordCount: number
  total: number
}

export interface SupplierDebtRawPayloadItem {
  index: number
  date: string | null
  direction: string
  amount: number
  counterparty: string
  counterpartyInn: string
  counterpartyAccount: string
  reference: string
  rawPayload: string
}

export interface SupplierDebtRawPayloadSource {
  source: string
  cached: boolean
  status: string
  message: string
  technicalDetails: string
  recordCount: number
  total: number
  payloads: SupplierDebtRawPayloadItem[]
}

export interface SupplierDebtRawPayloads {
  dateFrom: string
  dateTo: string
  generatedAt: string
  sources: SupplierDebtRawPayloadSource[]
}

export interface SupplierDebtUnmatchedGroup {
  groupKey: string
  provider: string
  matchText: string
  matchType: string
  counterparty: string
  counterpartyInn: string
  counterpartyAccount: string
  description: string
  amount: number
  transactionCount: number
  largestTransaction: number
  examples: SupplierDebtPayment[]
}

export interface SupplierDebtOverview {
  dateFrom: string
  dateTo: string
  purchaseTotal: number
  bogPaidTotal: number
  tbcPaidTotal: number
  cashPaidTotal: number
  bankPaidTotal: number
  paidTotal: number
  debtTotal: number
  supplierCount: number
  unmatchedPaymentTotal: number
  unmatchedPaymentCount: number
  suppliers: SupplierDebtRow[]
  unmatchedPayments: SupplierDebtPayment[]
  mappings: SupplierPaymentMapping[]
  sourceStatuses: SupplierDebtSourceStatus[]
  unmatchedPaymentGroups: SupplierDebtUnmatchedGroup[]
  snapshotGeneratedAt: string | null
  refreshInProgress: boolean
  lastRefreshStartedAt: string | null
  lastRefreshCompletedAt: string | null
  lastRefreshError: string
}

export interface SupplierCashPayment {
  id: string
  supplierKey: string
  supplierTin: string
  supplierName: string
  date: string
  amount: number
  note: string
  source: string
  createdAt: string
  updatedAt: string
}

export interface SupplierCashPaymentInput {
  supplierKey: string
  supplierTin: string
  supplierName: string
  date: string
  amount: number
  note: string
}
