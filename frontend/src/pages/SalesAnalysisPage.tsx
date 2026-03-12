import React, { useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import {
  AlertCircle,
  ArrowDownRight,
  ArrowUpRight,
  CalendarRange,
  Landmark,
  Play,
} from 'lucide-react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { format } from 'date-fns'
import { runSalesAnalysis } from '../api/sales-analysis.api'
import FileDropzone from '../components/common/FileDropzone'
import { getDefaultDateRange, formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type {
  SalesAggregation,
  SalesAnalysisAggregationBlock,
  SalesAnalysisMetric,
  SalesAnalysisPeriodRow,
  SalesAnalysisResult,
  SalesAnalysisStatus,
} from '../types'

const aggregationLabels: Record<SalesAggregation, string> = {
  DAY: env.salesAnalysisAggregationDayLabel,
  WEEK: env.salesAnalysisAggregationWeekLabel,
  MONTH: env.salesAnalysisAggregationMonthLabel,
}

export default function SalesAnalysisPage() {
  const defaults = getDefaultDateRange()
  const [salesFile, setSalesFile] = useState<File | null>(null)
  const [tbcFile, setTbcFile] = useState<File | null>(null)
  const [bogFile, setBogFile] = useState<File | null>(null)
  const [dateFrom, setDateFrom] = useState(defaults.from)
  const [dateTo, setDateTo] = useState(defaults.to)
  const [aggregation, setAggregation] = useState<SalesAggregation>('DAY')
  const [lastRunKey, setLastRunKey] = useState<string | null>(null)

  const currentRunKey = useMemo(() => {
    if (!salesFile || !tbcFile || !bogFile) return null
    return [
      salesFile.name,
      salesFile.size,
      tbcFile.name,
      tbcFile.size,
      bogFile.name,
      bogFile.size,
      dateFrom,
      dateTo,
    ].join(':')
  }, [salesFile, tbcFile, bogFile, dateFrom, dateTo])

  const needsRecalculation = Boolean(currentRunKey && currentRunKey !== lastRunKey)

  const mutation = useMutation({
    mutationFn: () => runSalesAnalysis(salesFile!, tbcFile!, bogFile!, dateFrom, dateTo),
    onSuccess: () => {
      if (currentRunKey) setLastRunKey(currentRunKey)
    },
  })

  const result = mutation.data
  const activeBlock = result ? getAggregationBlock(result, aggregation) : undefined
  const chartData = activeBlock?.periods.map((period) => ({
    period: formatPeriodLabel(period, aggregation),
    sales: period.sales,
    tbc: period.tbcIncome,
    bog: period.bogIncome,
    bank: period.bankIncome,
    variance: period.variance,
  })) ?? []

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6 rounded-3xl border border-slate-200 bg-gradient-to-br from-slate-950 via-slate-900 to-emerald-950 p-6 text-white shadow-xl">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-2xl">
            <p className="mb-2 inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-emerald-100">
              <Landmark className="h-3.5 w-3.5" />
              {env.navSalesAnalysisLabel}
            </p>
            <h1 className="text-3xl font-bold tracking-tight">{env.salesAnalysisTitle}</h1>
            <p className="mt-3 text-sm text-slate-200">{env.salesAnalysisInfo}</p>
          </div>
          <div className="grid min-w-[240px] grid-cols-2 gap-3 rounded-2xl bg-white/10 p-4 backdrop-blur">
            <HeroStat label={env.salesAnalysisAggregationDayLabel} value={result?.day.summary.periodCount ?? 0} />
            <HeroStat label={env.salesAnalysisAggregationWeekLabel} value={result?.week.summary.periodCount ?? 0} />
            <HeroStat label={env.salesAnalysisAggregationMonthLabel} value={result?.month.summary.periodCount ?? 0} />
            <HeroStat label={env.salesAnalysisCombinedBankColumnLabel} value={result ? formatGel(result.day.summary.totalBankIncome.current) : '--'} />
          </div>
        </div>
      </div>

      <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-base font-semibold text-slate-800">{env.salesAnalysisUploadTitle}</h2>
        <div className="grid gap-4 lg:grid-cols-3">
          <FileDropzone label={env.salesAnalysisSalesLabel} accept={env.salesAnalysisAccept} file={salesFile} onChange={(file) => { setSalesFile(file); mutation.reset() }} />
          <FileDropzone label={env.salesAnalysisTbcLabel} accept={env.salesAnalysisAccept} file={tbcFile} onChange={(file) => { setTbcFile(file); mutation.reset() }} />
          <FileDropzone label={env.salesAnalysisBogLabel} accept={env.salesAnalysisAccept} file={bogFile} onChange={(file) => { setBogFile(file); mutation.reset() }} />
        </div>

        <div className="mt-5 flex flex-wrap items-end gap-4">
          <DateField label={env.reconcileDateFromLabel} value={dateFrom} onChange={(value) => { setDateFrom(value); mutation.reset() }} />
          <DateField label={env.reconcileDateToLabel} value={dateTo} onChange={(value) => { setDateTo(value); mutation.reset() }} />
          <button
            onClick={() => mutation.mutate()}
            disabled={!salesFile || !tbcFile || !bogFile || mutation.isPending}
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Play className="h-4 w-4" />
            {mutation.isPending
              ? env.salesAnalysisRunningLabel
              : needsRecalculation
                ? env.salesAnalysisRecalculateLabel
                : env.salesAnalysisRunLabel}
          </button>
        </div>

        {currentRunKey && (
          <p className="mt-3 text-xs text-slate-500">
            {needsRecalculation ? env.salesAnalysisRequiresRecalcLabel : env.salesAnalysisAlreadyCalculatedLabel}
          </p>
        )}

        {mutation.isError && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            {mutation.error instanceof Error ? mutation.error.message : 'Error'}
          </div>
        )}
      </div>

      {activeBlock && (
        <>
          <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
            <div className="inline-flex rounded-2xl border border-slate-200 bg-white p-1 shadow-sm">
              {(['DAY', 'WEEK', 'MONTH'] as SalesAggregation[]).map((value) => (
                <button
                  key={value}
                  onClick={() => setAggregation(value)}
                  className={`rounded-xl px-4 py-2 text-sm font-semibold transition-colors ${
                    aggregation === value ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {aggregationLabels[value]}
                </button>
              ))}
            </div>
            <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 text-xs font-medium text-slate-600">
              <CalendarRange className="h-4 w-4" />
              {format(new Date(result!.dateFrom), 'dd MMM yyyy')} - {format(new Date(result!.dateTo), 'dd MMM yyyy')}
            </div>
          </div>

          <div className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <MetricCard label={env.salesAnalysisTotalSalesLabel} metric={activeBlock.summary.totalSales} accent="emerald" />
            <MetricCard label={env.salesAnalysisTotalBankLabel} metric={activeBlock.summary.totalBankIncome} accent="blue" />
            <MetricCard label={env.salesAnalysisVarianceLabel} metric={activeBlock.summary.variance} accent="amber" />
            <MetricCard label={env.salesAnalysisCaptureRatioLabel} metric={activeBlock.summary.captureRatio} accent="slate" isRatio />
            <MetricCard label={env.salesAnalysisAverageSalesLabel} metric={activeBlock.summary.averageSales} accent="emerald" />
            <MetricCard label={env.salesAnalysisAverageBankLabel} metric={activeBlock.summary.averageBankIncome} accent="blue" />
          </div>

          <div className="mb-6 grid gap-6 xl:grid-cols-2">
            <ChartCard title={env.salesAnalysisTrendChartTitle}>
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Legend />
                  <Line type="monotone" dataKey="sales" stroke="#059669" strokeWidth={2.5} dot={false} name={env.salesAnalysisSalesLabel} />
                  <Line type="monotone" dataKey="bank" stroke="#2563eb" strokeWidth={2.5} dot={false} name={env.salesAnalysisCombinedBankColumnLabel} />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title={env.salesAnalysisBreakdownChartTitle}>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Legend />
                  <Bar dataKey="sales" fill="#059669" radius={[6, 6, 0, 0]} name={env.salesAnalysisSalesLabel} />
                  <Bar dataKey="tbc" fill="#0f766e" radius={[6, 6, 0, 0]} name={env.salesAnalysisTbcLabel} />
                  <Bar dataKey="bog" fill="#7c3aed" radius={[6, 6, 0, 0]} name={env.salesAnalysisBogLabel} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <div className="mb-6">
            <ChartCard title={env.salesAnalysisVarianceChartTitle}>
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(value: number) => formatGel(value)} />
                  <Bar dataKey="variance" radius={[6, 6, 0, 0]} fill="#f59e0b" name={env.salesAnalysisVarianceLabel} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-200 px-5 py-4">
              <h2 className="text-base font-semibold text-slate-800">{env.salesAnalysisPeriodTableTitle}</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <HeaderCell>{env.salesAnalysisPeriodColumnLabel}</HeaderCell>
                    <HeaderCell>{env.salesAnalysisSalesColumnLabel}</HeaderCell>
                    <HeaderCell>{env.salesAnalysisTbcColumnLabel}</HeaderCell>
                    <HeaderCell>{env.salesAnalysisBogColumnLabel}</HeaderCell>
                    <HeaderCell>{env.salesAnalysisCombinedBankColumnLabel}</HeaderCell>
                    <HeaderCell>{env.salesAnalysisVarianceColumnLabel}</HeaderCell>
                    <HeaderCell>{env.salesAnalysisStatusColumnLabel}</HeaderCell>
                  </tr>
                </thead>
                <tbody>
                  {activeBlock.periods.map((period) => (
                    <tr key={period.key} className="border-t border-slate-100">
                      <BodyCell>{formatPeriodLabel(period, aggregation)}</BodyCell>
                      <BodyCell>{formatGel(period.sales)}</BodyCell>
                      <BodyCell>{formatGel(period.tbcIncome)}</BodyCell>
                      <BodyCell>{formatGel(period.bogIncome)}</BodyCell>
                      <BodyCell>{formatGel(period.bankIncome)}</BodyCell>
                      <BodyCell className={period.variance < 0 ? 'text-amber-700' : 'text-emerald-700'}>{formatGel(period.variance)}</BodyCell>
                      <BodyCell><StatusPill status={period.status} /></BodyCell>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function getAggregationBlock(result: SalesAnalysisResult, aggregation: SalesAggregation): SalesAnalysisAggregationBlock {
  if (aggregation === 'WEEK') return result.week
  if (aggregation === 'MONTH') return result.month
  return result.day
}

function formatPeriodLabel(period: SalesAnalysisPeriodRow, aggregation: SalesAggregation) {
  const from = new Date(period.dateFrom)
  const to = new Date(period.dateTo)
  if (aggregation === 'MONTH') return format(from, 'MMM yyyy')
  if (aggregation === 'WEEK') return `${format(from, 'dd MMM')} - ${format(to, 'dd MMM')}`
  return format(from, 'dd MMM yyyy')
}

function MetricCard({
  label,
  metric,
  accent,
  isRatio = false,
}: {
  label: string
  metric: SalesAnalysisMetric
  accent: 'emerald' | 'blue' | 'amber' | 'slate'
  isRatio?: boolean
}) {
  const accentClasses = {
    emerald: 'from-emerald-50 to-white border-emerald-200',
    blue: 'from-blue-50 to-white border-blue-200',
    amber: 'from-amber-50 to-white border-amber-200',
    slate: 'from-slate-100 to-white border-slate-200',
  }
  const positive = metric.delta >= 0

  return (
    <div className={`rounded-2xl border bg-gradient-to-br p-5 shadow-sm ${accentClasses[accent]}`}>
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">{label}</p>
      <p className="mt-3 text-2xl font-bold text-slate-900">
        {isRatio ? `${(metric.current * 100).toFixed(2)}%` : formatGel(metric.current)}
      </p>
      <div className={`mt-3 inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold ${positive ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
        {positive ? <ArrowUpRight className="h-3.5 w-3.5" /> : <ArrowDownRight className="h-3.5 w-3.5" />}
        {formatMetricDelta(metric, isRatio)}
      </div>
    </div>
  )
}

function formatMetricDelta(metric: SalesAnalysisMetric, isRatio: boolean) {
  const value = isRatio ? `${(metric.delta * 100).toFixed(2)}%` : formatGel(metric.delta)
  const percent = metric.deltaPercent ? ` (${(metric.deltaPercent * 100).toFixed(1)}%)` : ''
  return `${value}${percent}`
}

function StatusPill({ status }: { status: SalesAnalysisStatus }) {
  const map: Record<SalesAnalysisStatus, { label: string; className: string }> = {
    MATCH: { label: env.salesAnalysisStatusMatchLabel, className: 'bg-emerald-100 text-emerald-700' },
    SHORT: { label: env.salesAnalysisStatusShortLabel, className: 'bg-amber-100 text-amber-700' },
    OVER: { label: env.salesAnalysisStatusOverLabel, className: 'bg-blue-100 text-blue-700' },
    NO_BANK_DATA: { label: env.salesAnalysisStatusNoBankLabel, className: 'bg-rose-100 text-rose-700' },
    BANK_ONLY: { label: env.salesAnalysisStatusBankOnlyLabel, className: 'bg-violet-100 text-violet-700' },
  }

  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${map[status].className}`}>{map[status].label}</span>
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 text-base font-semibold text-slate-800">{title}</h2>
      {children}
    </div>
  )
}

function HeroStat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-2xl bg-white/10 p-3">
      <p className="text-[11px] uppercase tracking-[0.2em] text-slate-300">{label}</p>
      <p className="mt-2 text-lg font-semibold text-white">{value}</p>
    </div>
  )
}

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
      />
    </div>
  )
}

function HeaderCell({ children }: { children: React.ReactNode }) {
  return <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em]">{children}</th>
}

function BodyCell({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-4 py-3 text-slate-700 ${className}`}>{children}</td>
}
