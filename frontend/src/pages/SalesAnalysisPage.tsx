import React, { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  ArrowDownRight,
  ArrowUpRight,
  CalendarRange,
  Landmark,
  Play,
  Plus,
  Search,
  Sparkles,
  Tags,
  Trash2,
  ChevronDown,
} from 'lucide-react'
import {
  Bar,
  BarChart,
  Cell,
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
import { useNavigate } from 'react-router-dom'
import { deleteSalesEvent, listSalesEvents, suggestSalesEvents, upsertSalesEvent } from '../api/sales-events.api'
import { runSalesAnalysis } from '../api/sales-analysis.api'
import { listSalesProducts } from '../api/sales-products.api'
import FileDropzone from '../components/common/FileDropzone'
import { getDefaultDateRange, formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type {
  SalesAggregation,
  SalesAnalysisAggregationBlock,
  SalesAnalysisMetric,
  SalesAnalysisProductOption,
  SalesAnalysisProductPoint,
  SalesAnalysisProductSeries,
  SalesAnalysisPeriodRow,
  SalesAnalysisResult,
  SalesAnalysisStatus,
  SalesEvent,
} from '../types'

const aggregationLabels: Record<SalesAggregation, string> = {
  DAY: env.salesAnalysisAggregationDayLabel,
  WEEK: env.salesAnalysisAggregationWeekLabel,
  MONTH: env.salesAnalysisAggregationMonthLabel,
}

export default function SalesAnalysisPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const defaults = getDefaultDateRange()
  const [salesFile, setSalesFile] = useState<File | null>(null)
  const [tbcFile, setTbcFile] = useState<File | null>(null)
  const [bogFile, setBogFile] = useState<File | null>(null)
  const [dateFrom, setDateFrom] = useState(defaults.from)
  const [dateTo, setDateTo] = useState(defaults.to)
  const [aggregation, setAggregation] = useState<SalesAggregation>('DAY')
  const [lastRunKey, setLastRunKey] = useState<string | null>(null)
  const [eventDate, setEventDate] = useState(defaults.from)
  const [eventName, setEventName] = useState('')
  const [selectedEvents, setSelectedEvents] = useState<string[]>([])
  const [selectedProducts, setSelectedProducts] = useState<string[]>([])
  const [productsDropdownOpen, setProductsDropdownOpen] = useState(false)
  const [productSearch, setProductSearch] = useState('')

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

  const salesProductsQuery = useQuery({
    queryKey: ['sales-products', 'excluded-count'],
    queryFn: () => listSalesProducts(),
  })

  const salesEventsQuery = useQuery({
    queryKey: ['sales-events'],
    queryFn: listSalesEvents,
  })

  const suggestionsQuery = useQuery({
    queryKey: ['sales-events', 'suggest', eventName],
    queryFn: () => suggestSalesEvents(eventName),
    enabled: eventName.trim().length > 0,
  })

  const saveEventMutation = useMutation({
    mutationFn: () => upsertSalesEvent(eventDate, eventName),
    onSuccess: () => {
      setEventName('')
      queryClient.invalidateQueries({ queryKey: ['sales-events'] })
      if (result) {
        mutation.mutate()
      }
    },
  })

  const deleteEventMutation = useMutation({
    mutationFn: () => deleteSalesEvent(eventDate),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales-events'] })
      if (result) {
        mutation.mutate()
      }
    },
  })

  const result = mutation.data
  const baseBlock = result ? getAggregationBlock(result, aggregation) : undefined
  const activeBlock = useMemo(
    () => (baseBlock ? filterAggregationBlock(baseBlock, selectedEvents) : undefined),
    [baseBlock, selectedEvents]
  )
  const chartData = activeBlock?.periods.map((period) => ({
    period: formatPeriodLabel(period, aggregation),
    sales: period.sales,
    tbc: period.tbcIncome,
    bog: period.bogIncome,
    bank: period.bankIncome,
    variance: period.variance,
    events: period.events,
    hasEvents: period.events.length > 0,
  })) ?? []
  const excludedCount = salesProductsQuery.data?.filter((item) => item.excluded).length ?? 0
  const salesEvents = salesEventsQuery.data ?? []
  const eventComparison = useMemo(() => {
    if (!baseBlock) return null
    return buildEventComparison(baseBlock, selectedEvents)
  }, [baseBlock, selectedEvents])
  const availableProducts = activeBlock?.availableProducts ?? []

  useEffect(() => {
    if (!result) {
      setSelectedProducts([])
      return
    }
    setSelectedProducts(result.day.availableProducts.map((product) => product.productKey))
    setProductsDropdownOpen(false)
    setProductSearch('')
  }, [result])

  const selectedProductSeries = useMemo(
    () => (activeBlock?.productSeries ?? []).filter((series) => selectedProducts.includes(series.productKey)),
    [activeBlock, selectedProducts]
  )
  const visibleProductOptions = useMemo(() => {
    const query = productSearch.trim().toLowerCase()
    if (!query) {
      return availableProducts
    }
    return availableProducts.filter((product) => product.productName.toLowerCase().includes(query))
  }, [availableProducts, productSearch])
  const productClusterMap = useMemo(
    () => buildProductClusterMap(activeBlock?.productSeries ?? []),
    [activeBlock?.productSeries]
  )
  const selectedProductsLabel = useMemo(() => {
    if (selectedProducts.length === 0) {
      return env.salesAnalysisProductNoSelectionLabel
    }
    if (selectedProducts.length === availableProducts.length) {
      return `${env.salesAnalysisProductSelectorLabel}: ${selectedProducts.length}`
    }
    return `${env.salesAnalysisProductSelectorLabel}: ${selectedProducts.length}`
  }, [availableProducts.length, selectedProducts.length])
  const productGrossChart = useMemo(
    () => buildProductChartData(selectedProductSeries, activeBlock?.periods ?? [], aggregation, 'grossRevenue'),
    [selectedProductSeries, activeBlock?.periods, aggregation]
  )
  const productProfitChart = useMemo(
    () => buildProductChartData(selectedProductSeries, activeBlock?.periods ?? [], aggregation, 'profit'),
    [selectedProductSeries, activeBlock?.periods, aggregation]
  )
  const productQuantityChart = useMemo(
    () => buildProductChartData(selectedProductSeries, activeBlock?.periods ?? [], aggregation, 'quantity'),
    [selectedProductSeries, activeBlock?.periods, aggregation]
  )
  const productMarginChart = useMemo(
    () => buildProductChartData(selectedProductSeries, activeBlock?.periods ?? [], aggregation, 'profitPercentage'),
    [selectedProductSeries, activeBlock?.periods, aggregation]
  )
  const productClusterChart = useMemo(
    () => buildClusterProductChartData(activeBlock?.productSeries ?? [], selectedProducts, activeBlock?.periods ?? [], aggregation),
    [activeBlock?.productSeries, activeBlock?.periods, aggregation, selectedProducts]
  )

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
            <HeroStat label={env.salesAnalysisExcludedCountLabel} value={excludedCount} />
          </div>
        </div>
      </div>

      <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-semibold text-slate-800">{env.salesAnalysisUploadTitle}</h2>
          <button
            onClick={() => navigate(env.routeSalesProducts)}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
          >
            <Tags className="h-4 w-4" />
            {env.salesAnalysisManageProductsLabel}
          </button>
        </div>
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

      <div className="mb-6 grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-emerald-600" />
            <h2 className="text-base font-semibold text-slate-800">{env.salesAnalysisEventManagerTitle}</h2>
          </div>
          <div className="grid gap-4 md:grid-cols-[180px_minmax(0,1fr)_auto_auto]">
            <DateField label={env.salesAnalysisEventDateLabel} value={eventDate} onChange={setEventDate} />
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">{env.salesAnalysisEventNameLabel}</label>
              <input
                list="sales-event-suggestions"
                value={eventName}
                onChange={(e) => setEventName(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
              />
              <datalist id="sales-event-suggestions">
                {suggestionsQuery.data?.map((suggestion) => (
                  <option key={suggestion} value={suggestion} />
                ))}
              </datalist>
            </div>
            <button
              onClick={() => saveEventMutation.mutate()}
              disabled={!eventDate || !eventName.trim() || saveEventMutation.isPending}
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Plus className="h-4 w-4" />
              {env.salesAnalysisEventSaveLabel}
            </button>
            <button
              onClick={() => deleteEventMutation.mutate()}
              disabled={!eventDate || deleteEventMutation.isPending}
              className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Trash2 className="h-4 w-4" />
              {env.salesAnalysisEventDeleteLabel}
            </button>
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            {salesEvents.length === 0 && (
              <span className="text-sm text-slate-500">No events saved yet.</span>
            )}
            {salesEvents.map((event) => (
              <span
                key={`${event.date}:${event.name}`}
                className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700"
              >
                {format(new Date(event.date), 'dd MMM yyyy')}: {event.name}
              </span>
            ))}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-800">{env.salesAnalysisEventFilterLabel}</h2>
          <div className="flex flex-wrap gap-2">
            {result?.availableEvents.length
              ? result.availableEvents.map((name) => {
                  const active = selectedEvents.includes(name)
                  return (
                    <button
                      key={name}
                      onClick={() =>
                        setSelectedEvents((current) =>
                          current.includes(name)
                            ? current.filter((item) => item !== name)
                            : [...current, name]
                        )
                      }
                      className={`rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
                        active ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                      }`}
                    >
                      {name}
                    </button>
                  )
                })
              : <span className="text-sm text-slate-500">No events available in the selected data.</span>}
          </div>
        </div>
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

          {eventComparison && (
            <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-4 text-base font-semibold text-slate-800">{env.salesAnalysisEventCompareTitle}</h2>
              <div className="grid gap-4 md:grid-cols-2">
                <ComparisonCard title={env.salesAnalysisSelectedEventsLabel} rows={eventComparison.selected} />
                <ComparisonCard title={env.salesAnalysisNonEventsLabel} rows={eventComparison.other} />
              </div>
            </div>
          )}

          <div className="mb-6 grid gap-6 xl:grid-cols-2">
            <ChartCard title={env.salesAnalysisTrendChartTitle}>
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip content={<EventTooltip />} />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="sales"
                    stroke="#059669"
                    strokeWidth={2.5}
                    dot={<EventDot color="#059669" />}
                    name={env.salesAnalysisSalesLabel}
                  />
                  <Line
                    type="monotone"
                    dataKey="bank"
                    stroke="#2563eb"
                    strokeWidth={2.5}
                    dot={<EventDot color="#2563eb" />}
                    name={env.salesAnalysisCombinedBankColumnLabel}
                  />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title={env.salesAnalysisBreakdownChartTitle}>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip content={<EventTooltip />} />
                  <Legend />
                  <Bar dataKey="sales" fill="#059669" radius={[6, 6, 0, 0]} name={env.salesAnalysisSalesLabel}>
                    {chartData.map((entry) => (
                      <Cell key={`${entry.period}:sales`} fill={entry.hasEvents ? '#10b981' : '#059669'} />
                    ))}
                  </Bar>
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
                  <Tooltip content={<EventTooltip />} />
                  <Bar dataKey="variance" radius={[6, 6, 0, 0]} name={env.salesAnalysisVarianceLabel}>
                    {chartData.map((entry) => (
                      <Cell key={`${entry.period}:variance`} fill={entry.variance >= 0 ? '#f59e0b' : '#f97316'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-base font-semibold text-slate-800">{env.salesAnalysisProductSectionTitle}</h2>
              <span className="text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
                {env.salesAnalysisProductSelectorLabel}
              </span>
            </div>

            <div className="mb-5 relative">
              <button
                type="button"
                onClick={() => setProductsDropdownOpen((current) => !current)}
                className="flex w-full items-center justify-between rounded-2xl border border-slate-300 bg-slate-50 px-4 py-3 text-left text-sm font-semibold text-slate-800"
              >
                <span className="truncate">{selectedProductsLabel}</span>
                <ChevronDown className={`h-4 w-4 text-slate-500 transition-transform ${productsDropdownOpen ? 'rotate-180' : ''}`} />
              </button>

              {productsDropdownOpen && (
                <div className="absolute z-20 mt-2 w-full rounded-2xl border border-slate-200 bg-white p-3 shadow-xl">
                  <div className="mb-3 flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2">
                    <Search className="h-4 w-4 text-slate-400" />
                    <input
                      value={productSearch}
                      onChange={(e) => setProductSearch(e.target.value)}
                      placeholder={env.salesAnalysisProductSearchPlaceholder}
                      className="w-full border-0 bg-transparent text-sm text-slate-700 outline-none"
                    />
                  </div>
                  <div className="mb-3 flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => setSelectedProducts(selectClusterProductKeys(availableProducts, productClusterMap, 'TOP_10'))}
                      className="rounded-full bg-emerald-100 px-3 py-1.5 text-xs font-semibold text-emerald-800"
                    >
                      {env.salesAnalysisProductClusterTop10Label}
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedProducts(selectClusterProductKeys(availableProducts, productClusterMap, 'NEXT_30_A'))}
                      className="rounded-full bg-yellow-100 px-3 py-1.5 text-xs font-semibold text-yellow-800"
                    >
                      {env.salesAnalysisProductClusterNext30ALabel}
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedProducts(selectClusterProductKeys(availableProducts, productClusterMap, 'NEXT_30_B'))}
                      className="rounded-full bg-blue-100 px-3 py-1.5 text-xs font-semibold text-blue-800"
                    >
                      {env.salesAnalysisProductClusterNext30BLabel}
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedProducts(selectClusterProductKeys(availableProducts, productClusterMap, 'LAST_30'))}
                      className="rounded-full bg-red-100 px-3 py-1.5 text-xs font-semibold text-red-800"
                    >
                      {env.salesAnalysisProductClusterBottom30Label}
                    </button>
                  </div>
                  <div className="max-h-72 overflow-y-auto">
                    {visibleProductOptions.map((product) => {
                      const checked = selectedProducts.includes(product.productKey)
                      return (
                        <label
                          key={product.productKey}
                          className="flex cursor-pointer items-start justify-between gap-3 rounded-xl px-3 py-2 hover:bg-slate-50"
                        >
                          <span className="flex min-w-0 items-start gap-3">
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() =>
                                setSelectedProducts((current) =>
                                  current.includes(product.productKey)
                                    ? current.filter((item) => item !== product.productKey)
                                    : [...current, product.productKey]
                                )
                              }
                              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                            />
                            <span className="min-w-0">
                              <span className="block truncate text-sm font-medium text-slate-800">{product.productName}</span>
                              <span className="block text-[11px] text-slate-500">{formatGel(product.grossRevenueTotal)}</span>
                            </span>
                          </span>
                        </label>
                      )
                    })}
                    {visibleProductOptions.length === 0 && (
                      <div className="px-3 py-6 text-center text-sm text-slate-500">
                        {env.salesAnalysisProductSearchPlaceholder}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            {selectedProductSeries.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
                {env.salesAnalysisProductNoSelectionLabel}
              </div>
            ) : (
              <div className="grid gap-6 xl:grid-cols-2">
                <ProductChartCard
                  title={env.salesAnalysisProductGrossChartTitle}
                  chart={productGrossChart}
                  valueFormatter={(value) => formatGel(value)}
                />
                <ProductChartCard
                  title={env.salesAnalysisProductProfitChartTitle}
                  chart={productProfitChart}
                  valueFormatter={(value) => formatGel(value)}
                />
                <ProductChartCard
                  title={env.salesAnalysisProductQuantityChartTitle}
                  chart={productQuantityChart}
                  valueFormatter={(value) => formatNumber(value)}
                />
                <ProductChartCard
                  title={env.salesAnalysisProductMarginChartTitle}
                  chart={productMarginChart}
                  valueFormatter={(value) => `${(value * 100).toFixed(2)}%`}
                />
                <ProductChartCard
                  title={env.salesAnalysisProductClusterChartTitle}
                  chart={productClusterChart}
                  valueFormatter={(value) => formatGel(value)}
                />
              </div>
            )}
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
                    <HeaderCell>{env.salesAnalysisEventFilterLabel}</HeaderCell>
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
                      <BodyCell>
                        <div className="flex flex-wrap gap-1">
                          {period.events.length
                            ? period.events.map((event) => (
                                <span key={`${period.key}:${event}`} className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-700">
                                  {event}
                                </span>
                              ))
                            : <span className="text-slate-400">-</span>}
                        </div>
                      </BodyCell>
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

function filterAggregationBlock(
  block: SalesAnalysisAggregationBlock,
  selectedEvents: string[]
): SalesAnalysisAggregationBlock {
  if (selectedEvents.length === 0) {
    return block
  }
  const periods = block.periods.filter((period) =>
    period.events.some((event) => selectedEvents.includes(event))
  )
  const visibleKeys = new Set(periods.map((period) => period.key))
  return {
    ...block,
    periods,
    productSeries: block.productSeries.map((series) => ({
      ...series,
      periods: series.periods.filter((period) => visibleKeys.has(period.key)),
    })),
    summary: buildSummaryFromPeriods(periods),
  }
}

function buildSummaryFromPeriods(periods: SalesAnalysisPeriodRow[]) {
  const totalSales = periods.reduce((sum, period) => sum + period.sales, 0)
  const totalBankIncome = periods.reduce((sum, period) => sum + period.bankIncome, 0)
  const totalTbcIncome = periods.reduce((sum, period) => sum + period.tbcIncome, 0)
  const totalBogIncome = periods.reduce((sum, period) => sum + period.bogIncome, 0)
  const variance = totalBankIncome - totalSales
  const count = Math.max(periods.length, 1)

  return {
    periodCount: periods.length,
    totalSales: metric(totalSales),
    totalBankIncome: metric(totalBankIncome),
    totalTbcIncome: metric(totalTbcIncome),
    totalBogIncome: metric(totalBogIncome),
    variance: metric(variance),
    captureRatio: metric(totalSales === 0 ? 0 : totalBankIncome / totalSales),
    averageSales: metric(totalSales / count),
    averageBankIncome: metric(totalBankIncome / count),
  }
}

function buildEventComparison(block: SalesAnalysisAggregationBlock, selectedEvents: string[]) {
  const selected = block.periods.filter((period) =>
    selectedEvents.length === 0
      ? period.events.length > 0
      : period.events.some((event) => selectedEvents.includes(event))
  )
  const other = block.periods.filter((period) =>
    selectedEvents.length === 0
      ? period.events.length === 0
      : !period.events.some((event) => selectedEvents.includes(event))
  )
  return {
    selected: summarizeRows(selected),
    other: summarizeRows(other),
  }
}

function summarizeRows(rows: SalesAnalysisPeriodRow[]) {
  const totalSales = rows.reduce((sum, row) => sum + row.sales, 0)
  const totalBank = rows.reduce((sum, row) => sum + row.bankIncome, 0)
  return [
    { label: 'Periods', value: String(rows.length) },
    { label: env.salesAnalysisTotalSalesLabel, value: formatGel(totalSales) },
    { label: env.salesAnalysisTotalBankLabel, value: formatGel(totalBank) },
    { label: env.salesAnalysisVarianceLabel, value: formatGel(totalBank - totalSales) },
    { label: env.salesAnalysisCaptureRatioLabel, value: `${((totalSales === 0 ? 0 : totalBank / totalSales) * 100).toFixed(2)}%` },
  ]
}

function metric(current: number): SalesAnalysisMetric {
  return {
    current,
    previous: 0,
    delta: current,
    deltaPercent: 0,
  }
}

function formatPeriodLabel(period: SalesAnalysisPeriodRow, aggregation: SalesAggregation) {
  const from = new Date(period.dateFrom)
  const to = new Date(period.dateTo)
  if (aggregation === 'MONTH') return format(from, 'MMM yyyy')
  if (aggregation === 'WEEK') return `${format(from, 'dd MMM')} - ${format(to, 'dd MMM')}`
  return format(from, 'dd MMM yyyy')
}

function formatProductPeriodLabel(period: SalesAnalysisProductPoint, aggregation: SalesAggregation) {
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

function ComparisonCard({ title, rows }: { title: string; rows: Array<{ label: string; value: string }> }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <h3 className="mb-3 text-sm font-semibold text-slate-800">{title}</h3>
      <div className="space-y-2">
        {rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between gap-4 text-sm">
            <span className="text-slate-500">{row.label}</span>
            <span className="font-semibold text-slate-900">{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function EventDot(props: { cx?: number; cy?: number; payload?: { hasEvents?: boolean }; color: string }) {
  const { cx, cy, payload, color } = props
  if (!payload?.hasEvents || cx == null || cy == null) {
    return null
  }
  return <circle cx={cx} cy={cy} r={5} fill={color} stroke="#0f172a" strokeWidth={2} />
}

function EventTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean
  payload?: Array<{ value: number; name: string; payload?: { events?: string[] } }>
  label?: string
}) {
  if (!active || !payload?.length) {
    return null
  }
  const events = payload[0]?.payload?.events ?? []
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-lg">
      <p className="mb-2 text-sm font-semibold text-slate-900">{label}</p>
      <div className="space-y-1 text-xs text-slate-600">
        {payload.map((entry) => (
          <div key={entry.name} className="flex items-center justify-between gap-4">
            <span>{entry.name}</span>
            <span className="font-semibold text-slate-900">{formatGel(entry.value)}</span>
          </div>
        ))}
        {events.length > 0 && (
          <div className="border-t border-slate-100 pt-2">
            <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Events</p>
            <div className="flex flex-wrap gap-1">
              {events.map((event) => (
                <span key={event} className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-700">
                  {event}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

type ProductMetricKey = 'grossRevenue' | 'profit' | 'quantity' | 'profitPercentage'

type ProductChartSeriesMeta = {
  dataKey: string
  productName: string
  shortName: string
  color: string
  cluster?: ProductCluster
}

type ProductChartModel = {
  rows: Array<Record<string, number | string>>
  series: ProductChartSeriesMeta[]
}

function buildProductChartData(
  selectedSeries: SalesAnalysisProductSeries[],
  visiblePeriods: SalesAnalysisPeriodRow[],
  aggregation: SalesAggregation,
  metric: ProductMetricKey
): ProductChartModel {
  const visiblePeriodKeys = visiblePeriods.map((period) => period.key)
  const periodLabelByKey = new Map(
    selectedSeries.flatMap((series) =>
      series.periods.map((period) => [period.key, formatProductPeriodLabel(period, aggregation)] as const)
    )
  )

  const series = selectedSeries.map((item, index) => ({
    dataKey: `product_${index}`,
    productName: item.productName,
    shortName: shortenProductName(item.productName),
    color: PRODUCT_LINE_COLORS[index % PRODUCT_LINE_COLORS.length],
  }))

  const rows = visiblePeriodKeys.map((key) => {
    const row: Record<string, number | string> = {
      period: periodLabelByKey.get(key) ?? key,
    }
    selectedSeries.forEach((item, index) => {
      const point = item.periods.find((period) => period.key === key)
      row[`product_${index}`] = point?.[metric] ?? 0
    })
    return row
  })

  return { rows, series }
}

type ProductCluster = 'TOP_10' | 'NEXT_30_A' | 'NEXT_30_B' | 'LAST_30'

function buildClusterProductChartData(
  allSeries: SalesAnalysisProductSeries[],
  selectedProductKeys: string[],
  visiblePeriods: SalesAnalysisPeriodRow[],
  aggregation: SalesAggregation
): ProductChartModel {
  const selectedSeries = allSeries.filter((series) => selectedProductKeys.includes(series.productKey))
  const clusterByKey = buildProductClusterMap(allSeries)
  const visiblePeriodKeys = visiblePeriods.map((period) => period.key)
  const periodLabelByKey = new Map(
    selectedSeries.flatMap((series) =>
      series.periods.map((period) => [period.key, formatProductPeriodLabel(period, aggregation)] as const)
    )
  )

  const series = selectedSeries.map((item, index) => {
    const cluster = clusterByKey.get(item.productKey) ?? 'LAST_30'
    return {
      dataKey: `cluster_product_${index}`,
      productName: item.productName,
      shortName: shortenProductName(item.productName),
      color: PRODUCT_CLUSTER_COLORS[cluster],
      cluster,
    }
  })

  const rows = visiblePeriodKeys.map((key) => {
    const row: Record<string, number | string> = {
      period: periodLabelByKey.get(key) ?? key,
    }
    selectedSeries.forEach((item, index) => {
      const point = item.periods.find((period) => period.key === key)
      row[`cluster_product_${index}`] = point?.profit ?? 0
    })
    return row
  })

  return { rows, series }
}

function buildProductClusterMap(allSeries: SalesAnalysisProductSeries[]) {
  const ranked = allSeries
    .map((series) => ({
      productKey: series.productKey,
      totalProfit: series.periods.reduce((sum, period) => sum + period.profit, 0),
    }))
    .sort((left, right) => right.totalProfit - left.totalProfit)

  const total = ranked.length
  const top10Count = Math.min(total, Math.max(1, Math.ceil(total * 0.1)))
  const next30ACount = Math.min(Math.max(total - top10Count, 0), Math.ceil(total * 0.3))
  const next30BCount = Math.min(Math.max(total - top10Count - next30ACount, 0), Math.ceil(total * 0.3))

  const clusterMap = new Map<string, ProductCluster>()
  ranked.forEach((product, index) => {
    if (index < top10Count) {
      clusterMap.set(product.productKey, 'TOP_10')
      return
    }
    if (index < top10Count + next30ACount) {
      clusterMap.set(product.productKey, 'NEXT_30_A')
      return
    }
    if (index < top10Count + next30ACount + next30BCount) {
      clusterMap.set(product.productKey, 'NEXT_30_B')
      return
    }
    clusterMap.set(product.productKey, 'LAST_30')
  })

  return clusterMap
}

function selectClusterProductKeys(
  products: SalesAnalysisProductOption[],
  clusterMap: Map<string, ProductCluster>,
  cluster: ProductCluster
) {
  return products
    .filter((product) => clusterMap.get(product.productKey) === cluster)
    .map((product) => product.productKey)
}

function ProductChartCard({
  title,
  chart,
  valueFormatter,
}: {
  title: string
  chart: ProductChartModel
  valueFormatter: (value: number) => string
}) {
  return (
    <ChartCard title={title}>
          <ResponsiveContainer width="100%" height={300}>
        <LineChart data={chart.rows}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="period" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} />
          <Tooltip content={<ProductTooltip valueFormatter={valueFormatter} />} />
          {chart.series.map((series) => (
            <Line
              key={series.dataKey}
              type="monotone"
              dataKey={series.dataKey}
              stroke={series.color}
              strokeWidth={2.25}
              dot={false}
              legendType="none"
              name={series.shortName}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

function ProductTooltip({
  active,
  payload,
  valueFormatter,
}: {
  active?: boolean
  payload?: Array<{ value: number; color?: string; name?: string }>
  valueFormatter: (value: number) => string
}) {
  if (!active || !payload?.length) {
    return null
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-lg">
      <div className="space-y-1 text-xs text-slate-600">
        {[...payload]
          .sort((left, right) => right.value - left.value)
          .map((entry, index) => (
          <div key={`product-value-${index}`} className="flex items-center justify-between gap-4">
            <span className="inline-flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
              {entry.name || 'Product'}
            </span>
            <span className="font-semibold text-slate-900">{valueFormatter(entry.value)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('en-GB', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value)
}

function shortenProductName(value: string) {
  const normalized = value.trim()
  if (normalized.length <= 24) {
    return normalized
  }
  return `${normalized.slice(0, 21)}...`
}

const PRODUCT_LINE_COLORS = [
  '#0f766e',
  '#2563eb',
  '#7c3aed',
  '#dc2626',
  '#ea580c',
  '#65a30d',
  '#0891b2',
  '#be123c',
  '#4f46e5',
  '#16a34a',
] as const

const PRODUCT_CLUSTER_COLORS: Record<ProductCluster, string> = {
  TOP_10: '#16a34a',
  NEXT_30_A: '#eab308',
  NEXT_30_B: '#2563eb',
  LAST_30: '#dc2626',
}
