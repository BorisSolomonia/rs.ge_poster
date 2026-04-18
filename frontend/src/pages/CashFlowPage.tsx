import React, { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ChevronDown, ChevronRight, RefreshCcw, Wallet } from 'lucide-react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  getCashFlowCategoryDebug,
  getCashFlowCategories,
  getCashFlowOverview,
  getCashFlowStatus,
  getCashFlowTransactions,
  getCashFlowWarnings,
  refreshCashFlow,
} from '../api/cash-flow.api'
import { deleteCashFlowMapping, getCashFlowMappings, upsertCashFlowMapping } from '../api/cash-flow-mappings.api'
import { formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type {
  CashFlowAnalysisMetric,
  CashFlowCategoryDebugRow,
  CashFlowMonth,
  CashFlowTransaction,
  CashFlowUnmappedCategory,
  CashFlowWarning,
} from '../types'

const groupColors = ['#2563eb', '#059669', '#d97706', '#7c3aed', '#64748b']

export default function CashFlowPage() {
  const queryClient = useQueryClient()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [selectedMonth, setSelectedMonth] = useState('')
  const [expandedMonths, setExpandedMonths] = useState<string[]>([])
  const [drilldown, setDrilldown] = useState<{ month: string; group?: string; category?: string } | null>(null)
  const [mappingSelections, setMappingSelections] = useState<Record<string, string>>({})

  const statusQuery = useQuery({
    queryKey: ['cash-flow-status'],
    queryFn: getCashFlowStatus,
    refetchInterval: 30_000,
  })

  const overviewQuery = useQuery({
    queryKey: ['cash-flow-overview', from, to],
    queryFn: () => getCashFlowOverview(from || undefined, to || undefined),
    refetchInterval: 60_000,
  })

  useEffect(() => {
    const months = overviewQuery.data?.months ?? []
    if (!selectedMonth && months.length > 0) {
      setSelectedMonth(months[months.length - 1].month)
    }
    if (expandedMonths.length === 0 && months.length > 0) {
      setExpandedMonths([months[months.length - 1].month])
    }
  }, [overviewQuery.data, selectedMonth, expandedMonths.length])

  useEffect(() => {
    const months = overviewQuery.data?.months ?? []
    if (months.length === 0) {
      if (selectedMonth) {
        setSelectedMonth('')
      }
      return
    }
    if (!months.some((month) => month.month === selectedMonth)) {
      setSelectedMonth(months[months.length - 1].month)
    }
  }, [overviewQuery.data, selectedMonth])

  const refreshMutation = useMutation({
    mutationFn: refreshCashFlow,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cash-flow-status'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-overview'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-categories'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-warnings'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-transactions'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-income-debug'] }),
      ])
    },
  })

  const categoriesQuery = useQuery({
    queryKey: ['cash-flow-categories', selectedMonth],
    queryFn: () => getCashFlowCategories(selectedMonth),
    enabled: Boolean(selectedMonth),
  })

  const mappingsQuery = useQuery({
    queryKey: ['cash-flow-mappings', from, to],
    queryFn: () => getCashFlowMappings(from || undefined, to || undefined),
  })

  const warningsQuery = useQuery({
    queryKey: ['cash-flow-warnings', selectedMonth],
    queryFn: () => getCashFlowWarnings(selectedMonth || undefined),
    enabled: Boolean(selectedMonth),
  })

  const incomeDebugQuery = useQuery({
    queryKey: ['cash-flow-income-debug', from, to],
    queryFn: () => getCashFlowCategoryDebug('რეალიზაცია', from || undefined, to || undefined),
    enabled: Boolean(overviewQuery.data),
  })

  const transactionsQuery = useQuery({
    queryKey: ['cash-flow-transactions', drilldown?.month, drilldown?.group, drilldown?.category],
    queryFn: () => getCashFlowTransactions(drilldown!.month, drilldown?.group, drilldown?.category),
    enabled: Boolean(drilldown),
  })

  useEffect(() => {
    if (!mappingsQuery.data) {
      return
    }
    setMappingSelections((current) => {
      const next = { ...current }
      for (const mapping of mappingsQuery.data.mappings) {
        next[mapping.sourceCategory] = mapping.targetCategory
      }
      return next
    })
  }, [mappingsQuery.data])

  const mappingMutation = useMutation({
    mutationFn: ({ sourceCategory, targetCategory }: { sourceCategory: string; targetCategory: string }) =>
      upsertCashFlowMapping(sourceCategory, targetCategory),
    onSuccess: async (_, variables) => {
      setMappingSelections((current) => ({ ...current, [variables.sourceCategory]: variables.targetCategory }))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cash-flow-status'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-overview'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-categories'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-warnings'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-transactions'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-mappings'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-income-debug'] }),
      ])
    },
  })

  const deleteMappingMutation = useMutation({
    mutationFn: deleteCashFlowMapping,
    onSuccess: async (_, sourceCategory) => {
      setMappingSelections((current) => {
        const next = { ...current }
        delete next[sourceCategory]
        return next
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cash-flow-status'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-overview'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-categories'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-warnings'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-transactions'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-mappings'] }),
        queryClient.invalidateQueries({ queryKey: ['cash-flow-income-debug'] }),
      ])
    },
  })

  const months = overviewQuery.data?.months ?? []
  const selectedMonthData = months.find((month) => month.month === selectedMonth) ?? months[months.length - 1]
  const periodSummary = useMemo(() => {
    if (months.length === 0) {
      return null
    }
    return months.reduce((summary, month) => ({
      totalInflow: summary.totalInflow + month.totalInflow,
      totalOutflow: summary.totalOutflow + month.totalOutflow,
      netMovement: summary.netMovement + month.netMovement,
      endingCash: month.endingCash,
      endingBog: month.endingBog,
      endingTbc: month.endingTbc,
      totalEndingBalance: month.totalEndingBalance,
      warningCount: summary.warningCount + month.warningCount,
    }), {
      totalInflow: 0,
      totalOutflow: 0,
      netMovement: 0,
      endingCash: 0,
      endingBog: 0,
      endingTbc: 0,
      totalEndingBalance: 0,
      warningCount: 0,
    })
  }, [months])
  const trendData = months.map((month) => ({
    month: month.month,
    inflow: month.totalInflow,
    outflow: month.totalOutflow,
    net: month.netMovement,
    balance: month.totalEndingBalance,
  }))
  const expenseData = useMemo(
    () =>
      (categoriesQuery.data ?? [])
        .flatMap((group) => group.group === 'EXPENSE' ? group.categories : [])
        .sort((left, right) => right.amount - left.amount)
        .slice(0, 6)
        .map((category, index) => ({
          name: category.category,
          value: category.amount,
          color: groupColors[index % groupColors.length],
        })),
    [categoriesQuery.data]
  )

  return (
    <div className="mx-auto max-w-7xl p-6">
      <div className="mb-6 rounded-3xl border border-slate-200 bg-gradient-to-br from-slate-950 via-slate-900 to-sky-950 p-6 text-white shadow-xl">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-2xl">
            <p className="mb-2 inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-sky-100">
              <Wallet className="h-3.5 w-3.5" />
              {env.navCashFlowLabel}
            </p>
            <h1 className="text-3xl font-bold tracking-tight">{env.cashFlowTitle}</h1>
            <p className="mt-3 text-sm text-slate-200">{env.cashFlowInfo}</p>
          </div>
          <div className="min-w-[280px] rounded-2xl bg-white/10 p-4 backdrop-blur">
            <div className="grid gap-3 sm:grid-cols-2">
              <StatusLine label={env.cashFlowStatusLabel} value={statusQuery.data?.status ?? 'IDLE'} />
              <StatusLine label={env.cashFlowLastSyncLabel} value={formatDateTime(statusQuery.data?.lastSuccessAt)} />
            </div>
            {statusQuery.data?.lastError && (
              <div className="mt-3 rounded-xl border border-red-300/40 bg-red-500/10 p-3 text-xs text-red-100">
                {statusQuery.data.lastError}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-end gap-4">
          <MonthField label={env.cashFlowMonthFromLabel} value={from} onChange={setFrom} />
          <MonthField label={env.cashFlowMonthToLabel} value={to} onChange={setTo} />
          <button
            onClick={() => refreshMutation.mutate()}
            disabled={refreshMutation.isPending || statusQuery.data?.refreshInProgress}
            className="inline-flex items-center gap-2 rounded-xl bg-sky-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <RefreshCcw className={`h-4 w-4 ${refreshMutation.isPending ? 'animate-spin' : ''}`} />
            {env.cashFlowRefreshLabel}
          </button>
        </div>
      </div>

      {!selectedMonthData && (
        <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center text-slate-500 shadow-sm">
          {env.cashFlowNoDataLabel}
        </div>
      )}

      {selectedMonthData && periodSummary && (
        <>
          <div className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard label={env.cashFlowTotalInflowLabel} value={periodSummary.totalInflow} tone="emerald" />
            <MetricCard label={env.cashFlowTotalOutflowLabel} value={periodSummary.totalOutflow} tone="amber" />
            <MetricCard label={env.cashFlowNetMovementLabel} value={periodSummary.netMovement} tone="sky" />
            <MetricCard label={env.cashFlowEndingCashLabel} value={periodSummary.endingCash} tone="slate" />
            <MetricCard label={env.cashFlowEndingBogLabel} value={periodSummary.endingBog} tone="sky" />
            <MetricCard label={env.cashFlowEndingTbcLabel} value={periodSummary.endingTbc} tone="violet" />
            <MetricCard label={env.cashFlowEndingTotalLabel} value={periodSummary.totalEndingBalance} tone="emerald" />
            <MetricCard label={env.cashFlowWarningStateLabel} value={periodSummary.warningCount} tone="rose" isCount />
          </div>

          {overviewQuery.data?.analysis && (
            <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="mb-4">
                <h2 className="text-base font-semibold text-slate-800">{env.cashFlowAnalysisTitle}</h2>
                <p className="mt-1 text-sm text-slate-500">{formatPeriodLabel(overviewQuery.data.analysis.currentPeriod)}</p>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <HeaderCell>{env.cashFlowCategoryColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAnalysisCurrentLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAnalysisPreviousMonthLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAnalysisDeltaLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAnalysisPreviousYearLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAnalysisDeltaLabel}</HeaderCell>
                    </tr>
                  </thead>
                  <tbody>
                    <AnalysisRow
                      label={env.cashFlowTotalInflowLabel}
                      metric={overviewQuery.data.analysis.totalInflow}
                      previousMonthPeriod={overviewQuery.data.analysis.previousMonthPeriod}
                      previousYearPeriod={overviewQuery.data.analysis.previousYearPeriod}
                    />
                    <AnalysisRow
                      label={env.cashFlowTotalOutflowLabel}
                      metric={overviewQuery.data.analysis.totalOutflow}
                      previousMonthPeriod={overviewQuery.data.analysis.previousMonthPeriod}
                      previousYearPeriod={overviewQuery.data.analysis.previousYearPeriod}
                    />
                    <AnalysisRow
                      label={env.cashFlowNetMovementLabel}
                      metric={overviewQuery.data.analysis.netMovement}
                      previousMonthPeriod={overviewQuery.data.analysis.previousMonthPeriod}
                      previousYearPeriod={overviewQuery.data.analysis.previousYearPeriod}
                    />
                    <AnalysisRow
                      label={env.cashFlowEndingTotalLabel}
                      metric={overviewQuery.data.analysis.totalEndingBalance}
                      previousMonthPeriod={overviewQuery.data.analysis.previousMonthPeriod}
                      previousYearPeriod={overviewQuery.data.analysis.previousYearPeriod}
                    />
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <div className="mb-6 grid gap-6 xl:grid-cols-2">
            <ChartCard title={env.cashFlowTrendInOutTitle}>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Legend />
                  <Bar dataKey="inflow" fill="#059669" name={env.cashFlowTotalInflowLabel} />
                  <Bar dataKey="outflow" fill="#d97706" name={env.cashFlowTotalOutflowLabel} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title={env.cashFlowTrendNetTitle}>
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Area type="monotone" dataKey="net" stroke="#2563eb" fill="#bfdbfe" name={env.cashFlowNetMovementLabel} />
                </AreaChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title={env.cashFlowTrendBalanceTitle}>
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Area type="monotone" dataKey="balance" stroke="#0f766e" fill="#a7f3d0" name={env.cashFlowEndingTotalLabel} />
                </AreaChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title={env.cashFlowExpenseCompositionTitle}>
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie data={expenseData} dataKey="value" nameKey="name" outerRadius={88} innerRadius={48}>
                    {expenseData.map((entry) => (
                      <Cell key={entry.name} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <div className="mb-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-200 px-5 py-4">
              <h2 className="text-base font-semibold text-slate-800">{env.cashFlowTableTitle}</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <HeaderCell>{env.cashFlowMonthColumnLabel}</HeaderCell>
                    <HeaderCell>{env.cashFlowTotalInflowLabel}</HeaderCell>
                    <HeaderCell>{env.cashFlowTotalOutflowLabel}</HeaderCell>
                    <HeaderCell>{env.cashFlowNetMovementLabel}</HeaderCell>
                    <HeaderCell>{env.cashFlowEndingTotalLabel}</HeaderCell>
                    <HeaderCell>{env.cashFlowWarningStateLabel}</HeaderCell>
                  </tr>
                </thead>
                <tbody>
                  {months.map((month) => {
                    const expanded = expandedMonths.includes(month.month)
                    return (
                      <React.Fragment key={month.month}>
                        <tr className="border-t border-slate-100">
                          <BodyCell>
                            <button
                              onClick={() =>
                                setExpandedMonths((current) =>
                                  current.includes(month.month)
                                    ? current.filter((item) => item !== month.month)
                                    : [...current, month.month]
                                )
                              }
                              className="inline-flex items-center gap-2 font-semibold text-slate-800"
                            >
                              {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                              {month.month}
                            </button>
                          </BodyCell>
                          <BodyCell>{formatGel(month.totalInflow)}</BodyCell>
                          <BodyCell>{formatGel(month.totalOutflow)}</BodyCell>
                          <BodyCell className={month.netMovement < 0 ? 'text-amber-700' : 'text-emerald-700'}>{formatGel(month.netMovement)}</BodyCell>
                          <BodyCell>{formatGel(month.totalEndingBalance)}</BodyCell>
                          <BodyCell>{month.warningCount}</BodyCell>
                        </tr>
                        {expanded && month.groups.map((group) => (
                          <React.Fragment key={`${month.month}:${group.group}`}>
                            <tr className="border-t border-slate-50 bg-slate-50/70">
                              <BodyCell className="pl-10 font-semibold text-slate-700">{group.group}</BodyCell>
                              <BodyCell>{''}</BodyCell>
                              <BodyCell>{''}</BodyCell>
                              <BodyCell>{formatGel(group.amount)}</BodyCell>
                              <BodyCell>{''}</BodyCell>
                              <BodyCell>{group.transactionCount}</BodyCell>
                            </tr>
                            {group.categories.map((category) => (
                              <tr
                                key={`${month.month}:${group.group}:${category.category}`}
                                className="cursor-pointer border-t border-slate-50 hover:bg-slate-50"
                                onClick={() => {
                                  setSelectedMonth(month.month)
                                  setDrilldown({ month: month.month, group: group.group, category: category.category })
                                }}
                              >
                                <BodyCell className="pl-16 text-slate-600">{category.category}</BodyCell>
                                <BodyCell>{''}</BodyCell>
                                <BodyCell>{''}</BodyCell>
                                <BodyCell>{formatGel(category.amount)}</BodyCell>
                                <BodyCell>{''}</BodyCell>
                                <BodyCell>{category.transactionCount}</BodyCell>
                              </tr>
                            ))}
                          </React.Fragment>
                        ))}
                      </React.Fragment>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="mb-6 grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="mb-4 flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 text-amber-600" />
                <h2 className="text-base font-semibold text-slate-800">{env.cashFlowWarningsTitle}</h2>
              </div>
              <div className="space-y-3">
                {(warningsQuery.data?.warnings ?? []).slice(0, 12).map((warning) => (
                  <WarningItem key={`${warning.month}:${warning.sourceRow}:${warning.code}`} warning={warning} />
                ))}
                {warningsQuery.data && warningsQuery.data.total === 0 && (
                  <div className="rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700">No warnings for this month.</div>
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-4 text-base font-semibold text-slate-800">{env.cashFlowDrilldownTitle}</h2>
              {!drilldown && (
                <div className="rounded-xl border border-dashed border-slate-300 p-6 text-sm text-slate-500">
                  Click a category row in the monthly cash flow table to inspect source transactions.
                </div>
              )}
              {drilldown && (
                <div className="space-y-4">
                  <div className="rounded-xl bg-slate-50 p-3 text-sm text-slate-700">
                    <span className="font-semibold">{drilldown.month}</span>
                    {drilldown.group ? ` • ${drilldown.group}` : ''}
                    {drilldown.category ? ` • ${drilldown.category}` : ''}
                  </div>
                  <div className="max-h-[420px] overflow-auto">
                    <table className="min-w-full text-sm">
                      <thead className="bg-slate-50 text-slate-500">
                        <tr>
                          <HeaderCell>{env.cashFlowSourceRowColumnLabel}</HeaderCell>
                          <HeaderCell>{env.cashFlowSourceCategoryColumnLabel}</HeaderCell>
                          <HeaderCell>{env.cashFlowCategoryColumnLabel}</HeaderCell>
                          <HeaderCell>{env.cashFlowAmountColumnLabel}</HeaderCell>
                          <HeaderCell>{env.cashFlowIssuesColumnLabel}</HeaderCell>
                        </tr>
                      </thead>
                      <tbody>
                        {(transactionsQuery.data?.transactions ?? []).map((transaction) => (
                          <TransactionRow key={`${transaction.sourceRow}:${transaction.category}`} transaction={transaction} />
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-4 flex flex-wrap items-start justify-between gap-4">
              <div>
                <h2 className="text-base font-semibold text-slate-800">{env.cashFlowUnmappedTitle}</h2>
                <p className="mt-1 text-sm text-slate-500">{env.cashFlowUnmappedInfo}</p>
              </div>
              <div className="rounded-xl bg-rose-50 px-4 py-3 text-sm font-semibold text-rose-700">
                {env.cashFlowUnmappedTotalLabel}: {formatGel(overviewQuery.data?.unmappedTotal ?? 0)}
              </div>
            </div>

            {(overviewQuery.data?.unmappedCategories ?? []).length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 p-6 text-sm text-slate-500">
                All source categories are mapped.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <HeaderCell>{env.cashFlowSourceCategoryColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAmountColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowTransactionCountLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowMapTargetLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowIssuesColumnLabel}</HeaderCell>
                    </tr>
                  </thead>
                  <tbody>
                    {(overviewQuery.data?.unmappedCategories ?? []).map((category) => (
                      <UnmappedCategoryRow
                        key={category.sourceCategory}
                        category={category}
                        options={mappingsQuery.data?.canonicalCategories ?? []}
                        selectedTarget={mappingSelections[category.sourceCategory] ?? ''}
                        onSelect={(targetCategory) =>
                          setMappingSelections((current) => ({ ...current, [category.sourceCategory]: targetCategory }))
                        }
                        onSave={() => {
                          const targetCategory = mappingSelections[category.sourceCategory]
                          if (targetCategory) {
                            mappingMutation.mutate({ sourceCategory: category.sourceCategory, targetCategory })
                          }
                        }}
                        onDelete={() => deleteMappingMutation.mutate(category.sourceCategory)}
                        isSaving={
                          mappingMutation.isPending && mappingMutation.variables?.sourceCategory === category.sourceCategory
                        }
                        isDeleting={
                          deleteMappingMutation.isPending
                          && deleteMappingMutation.variables === category.sourceCategory
                        }
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-4 flex flex-wrap items-start justify-between gap-4">
              <div>
                <h2 className="text-base font-semibold text-slate-800">{env.cashFlowIncomeDebugTitle}</h2>
                <p className="mt-1 text-sm text-slate-500">
                  {formatGel(incomeDebugQuery.data?.totalAmount ?? 0)} • {env.cashFlowIncomeDebugIncludedLabel}: {incomeDebugQuery.data?.includedRowCount ?? 0} • {env.cashFlowIncomeDebugExcludedLabel}: {incomeDebugQuery.data?.excludedRowCount ?? 0}
                </p>
              </div>
            </div>
            <div className="grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
              <div className="rounded-xl bg-slate-50 p-4">
                <h3 className="mb-3 text-sm font-semibold text-slate-700">Months</h3>
                <div className="space-y-2">
                  {(incomeDebugQuery.data?.months ?? []).map((month) => (
                    <div key={month.month} className="flex items-center justify-between rounded-lg bg-white px-3 py-2 text-sm">
                      <span className="font-medium text-slate-700">{month.month}</span>
                      <span className="font-semibold text-slate-900">{formatGel(month.amount)}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <HeaderCell>{env.cashFlowSourceRowColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowSourceCategoryColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowCategoryColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowAmountColumnLabel}</HeaderCell>
                      <HeaderCell>{env.cashFlowIssuesColumnLabel}</HeaderCell>
                    </tr>
                  </thead>
                  <tbody>
                    {(incomeDebugQuery.data?.rows ?? []).slice(0, 25).map((row) => (
                      <IncomeDebugRow key={`${row.sourceRow}:${row.date}`} row={row} />
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function MetricCard({ label, value, tone, isCount = false }: { label: string; value: number; tone: 'emerald' | 'amber' | 'sky' | 'slate' | 'violet' | 'rose'; isCount?: boolean }) {
  const tones = {
    emerald: 'from-emerald-50 to-white border-emerald-200',
    amber: 'from-amber-50 to-white border-amber-200',
    sky: 'from-sky-50 to-white border-sky-200',
    slate: 'from-slate-100 to-white border-slate-200',
    violet: 'from-violet-50 to-white border-violet-200',
    rose: 'from-rose-50 to-white border-rose-200',
  }
  return (
    <div className={`rounded-2xl border bg-gradient-to-br p-5 shadow-sm ${tones[tone]}`}>
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">{label}</p>
      <p className="mt-3 text-2xl font-bold text-slate-900">{isCount ? value : formatGel(value)}</p>
    </div>
  )
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 text-base font-semibold text-slate-800">{title}</h2>
      {children}
    </div>
  )
}

function HeaderCell({ children }: { children: React.ReactNode }) {
  return <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em]">{children}</th>
}

function BodyCell({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-4 py-3 text-slate-700 ${className}`}>{children}</td>
}

function AnalysisRow({
  label,
  metric,
  previousMonthPeriod,
  previousYearPeriod,
}: {
  label: string
  metric: CashFlowAnalysisMetric
  previousMonthPeriod: { dateFrom: string | null; dateTo: string | null; available: boolean }
  previousYearPeriod: { dateFrom: string | null; dateTo: string | null; available: boolean }
}) {
  return (
    <tr className="border-t border-slate-100 align-top">
      <BodyCell className="font-semibold text-slate-800">{label}</BodyCell>
      <BodyCell>{formatAnalysisValue(metric.currentValue)}</BodyCell>
      <BodyCell>
        <div>{previousMonthPeriod.available ? formatAnalysisValue(metric.previousMonthValue) : env.cashFlowAnalysisNoComparisonLabel}</div>
        <div className="text-xs text-slate-500">{formatPeriodLabel(previousMonthPeriod)}</div>
      </BodyCell>
      <BodyCell>{formatDelta(metric.previousMonthDelta.amount, metric.previousMonthDelta.percent)}</BodyCell>
      <BodyCell>
        <div>{previousYearPeriod.available ? formatAnalysisValue(metric.previousYearValue) : env.cashFlowAnalysisNoComparisonLabel}</div>
        <div className="text-xs text-slate-500">{formatPeriodLabel(previousYearPeriod)}</div>
      </BodyCell>
      <BodyCell>{formatDelta(metric.previousYearDelta.amount, metric.previousYearDelta.percent)}</BodyCell>
    </tr>
  )
}

function MonthField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
      />
    </div>
  )
}

function StatusLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white/10 p-3">
      <p className="text-[11px] uppercase tracking-[0.2em] text-slate-300">{label}</p>
      <p className="mt-1 text-sm font-semibold text-white">{value}</p>
    </div>
  )
}

function WarningItem({ warning }: { warning: CashFlowWarning }) {
  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
      <div className="flex items-center justify-between gap-4">
        <span className="font-semibold">{warning.code}</span>
        <span className="text-xs">{warning.month} • row {warning.sourceRow}</span>
      </div>
      <p className="mt-1 text-xs">{warning.message}</p>
    </div>
  )
}

function TransactionRow({ transaction }: { transaction: CashFlowTransaction }) {
  const amount = transaction.cashInflow + transaction.bogInflow + transaction.tbcInflow
    - transaction.cashOutflow - transaction.bogOutflow - transaction.tbcOutflow
    - transaction.materialValue - transaction.serviceValue
  return (
    <tr className="border-t border-slate-100 align-top">
      <BodyCell>{transaction.sourceRow}</BodyCell>
      <BodyCell>
        <div className="font-semibold text-slate-800">{transaction.sourceCategory}</div>
      </BodyCell>
      <BodyCell>
        <div className="font-semibold text-slate-800">{transaction.category}</div>
        <div className="text-xs text-slate-500">{transaction.date ?? transaction.month}</div>
        {transaction.counterparty && <div className="text-xs text-slate-500">{transaction.counterparty}</div>}
      </BodyCell>
      <BodyCell>{formatGel(amount)}</BodyCell>
      <BodyCell>
        <div className="flex flex-wrap gap-1">
          {transaction.issues.length
            ? transaction.issues.map((issue) => (
                <span key={issue} className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-700">
                  {issue}
                </span>
              ))
            : <span className="text-slate-400">-</span>}
        </div>
      </BodyCell>
    </tr>
  )
}

function UnmappedCategoryRow({
  category,
  options,
  selectedTarget,
  onSelect,
  onSave,
  onDelete,
  isSaving,
  isDeleting,
}: {
  category: CashFlowUnmappedCategory
  options: string[]
  selectedTarget: string
  onSelect: (value: string) => void
  onSave: () => void
  onDelete: () => void
  isSaving: boolean
  isDeleting: boolean
}) {
  return (
    <tr className="border-t border-slate-100 align-top">
      <BodyCell className="font-semibold text-slate-800">{category.sourceCategory}</BodyCell>
      <BodyCell>{formatGel(category.amount)}</BodyCell>
      <BodyCell>{category.transactionCount}</BodyCell>
      <BodyCell>
        <select
          value={selectedTarget}
          onChange={(e) => onSelect(e.target.value)}
          className="min-w-[220px] rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
        >
          <option value="">{env.cashFlowSelectCategoryLabel}</option>
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </BodyCell>
      <BodyCell>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={onSave}
            disabled={!selectedTarget || isSaving}
            className="rounded-xl bg-sky-600 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {env.cashFlowMapSaveLabel}
          </button>
          <button
            onClick={onDelete}
            disabled={isDeleting}
            className="rounded-xl border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {env.cashFlowMapDeleteLabel}
          </button>
        </div>
      </BodyCell>
    </tr>
  )
}

function IncomeDebugRow({ row }: { row: CashFlowCategoryDebugRow }) {
  return (
    <tr className="border-t border-slate-100 align-top">
      <BodyCell>{row.sourceRow}</BodyCell>
      <BodyCell>
        <div className="font-semibold text-slate-800">{row.sourceCategory}</div>
        <div className="text-xs text-slate-500">{row.normalizedSourceCategory}</div>
      </BodyCell>
      <BodyCell>
        <div className="font-semibold text-slate-800">{row.effectiveCategory}</div>
        <div className="text-xs text-slate-500">{row.group}</div>
      </BodyCell>
      <BodyCell>{formatGel(row.incomeAmount)}</BodyCell>
      <BodyCell>
        <div className="space-y-1">
          <div className={`text-xs font-semibold ${row.countedAsIncome ? 'text-emerald-700' : 'text-amber-700'}`}>
            {row.classificationReason}
          </div>
          <div className="text-[11px] text-slate-500">
            raw H/K/N: [{row.rawCashInflow || '-'} / {row.rawBogInflow || '-'} / {row.rawTbcInflow || '-'}]
          </div>
          <div className="text-[11px] text-slate-500">
            parsed H/K/N: [{formatGel(row.cashInflow)} / {formatGel(row.bogInflow)} / {formatGel(row.tbcInflow)}]
          </div>
          {row.issues.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {row.issues.map((issue) => (
                <span key={issue} className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-700">
                  {issue}
                </span>
              ))}
            </div>
          )}
        </div>
      </BodyCell>
    </tr>
  )
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return '--'
  return new Date(value).toLocaleString()
}

function formatAnalysisValue(value: number | null) {
  if (value === null) {
    return env.cashFlowAnalysisNoComparisonLabel
  }
  return formatGel(value)
}

function formatDelta(amount: number | null, percent: number | null) {
  if (amount === null) {
    return env.cashFlowAnalysisNoComparisonLabel
  }
  const amountLabel = `${amount >= 0 ? '+' : ''}${formatGel(amount)}`
  const percentLabel = percent === null ? env.cashFlowAnalysisNoComparisonLabel : `${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%`
  return (
    <div>
      <div>{amountLabel}</div>
      <div className="text-xs text-slate-500">{percentLabel}</div>
    </div>
  )
}

function formatPeriodLabel(period: { dateFrom: string | null; dateTo: string | null; available: boolean }) {
  if (!period.available || !period.dateFrom || !period.dateTo) {
    return env.cashFlowAnalysisNoComparisonLabel
  }
  return `${period.dateFrom} - ${period.dateTo}`
}
