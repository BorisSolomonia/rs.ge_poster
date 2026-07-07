import { Fragment, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ChevronDown, ChevronRight, FolderTree, ListChecks, RefreshCcw, Trash2 } from 'lucide-react'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  categorizeTransaction,
  createCashFlowCategory,
  deleteCashFlowCategory,
  deleteCashFlowRule,
  getCashFlowCategories,
  getCashFlowMatrix,
  getCashFlowRules,
  getCashFlowStatus,
  getCashFlowTransactions,
  refreshCashFlow,
  upsertCashFlowRule,
  type CategorizeScope,
} from '../api/cash-flow.api'
import Drawer from '../components/common/Drawer'
import ChoiceDialog from '../components/common/ChoiceDialog'
import { formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type {
  CashFlowCategory,
  CashFlowMatchType,
  CashFlowMatrix,
  CashFlowMatrixCategory,
  CashFlowRule,
  CashFlowSectionKey,
  CashFlowTransaction,
} from '../types'

const EXPENSE_COLORS = ['#0e7490', '#0891b2', '#38bdf8', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6']
const OTHER_COLOR = '#94a3b8'
const SALES_COLOR = '#16a34a'
const NET_COLOR = '#0f172a'
const TOP_EXPENSES = 7

const SECTION_OPTIONS: { key: CashFlowSectionKey; nameKa: string }[] = [
  { key: 'OPERATING', nameKa: 'საოპერაციო საქმიანობა' },
  { key: 'INVESTING', nameKa: 'საინვესტიციო საქმიანობა' },
  { key: 'FINANCING', nameKa: 'საფინანსო საქმიანობა' },
]

const DEFAULT_FROM = '2025-01-01'

function formatLocalDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function today() {
  return formatLocalDate(new Date())
}

function cell(value: number) {
  return value === 0 ? '—' : formatGel(value)
}

function directionOf(txn: CashFlowTransaction): 'INFLOW' | 'OUTFLOW' {
  return txn.direction?.toUpperCase() === 'CREDIT' ? 'INFLOW' : 'OUTFLOW'
}

/** Categories of the given direction, in tree order, sub-categories prefixed for the picker. */
function categoryOptionsForDirection(categories: CashFlowCategory[], direction: 'INFLOW' | 'OUTFLOW') {
  const matching = categories.filter((category) => category.direction === direction)
  const options: { id: string; label: string }[] = []
  for (const parent of matching.filter((category) => !category.parentId)) {
    options.push({ id: parent.id, label: parent.nameKa })
    for (const child of matching.filter((category) => category.parentId === parent.id)) {
      options.push({ id: child.id, label: `— ${child.nameKa}` })
    }
  }
  return options
}

type Drilldown = { categoryId: string; categoryNameKa: string; month: string | null }
type PendingChange = { txn: CashFlowTransaction; categoryId: string }

export default function CashFlowPage() {
  const queryClient = useQueryClient()
  const [dateFrom, setDateFrom] = useState(DEFAULT_FROM)
  const [dateTo, setDateTo] = useState(today)
  const [openSections, setOpenSections] = useState<Set<string>>(new Set(['OPERATING']))
  const [openDirections, setOpenDirections] = useState<Set<string>>(new Set(['OPERATING|INFLOW', 'OPERATING|OUTFLOW']))
  const [openCategories, setOpenCategories] = useState<Set<string>>(new Set())
  const [drilldown, setDrilldown] = useState<Drilldown | null>(null)
  const [pendingChange, setPendingChange] = useState<PendingChange | null>(null)
  const [mappingOpen, setMappingOpen] = useState(false)
  const [categoriesOpen, setCategoriesOpen] = useState(false)

  const matrixQuery = useQuery({
    queryKey: ['cash-flow-matrix', dateFrom, dateTo],
    queryFn: () => getCashFlowMatrix(dateFrom, dateTo),
  })
  const categoriesQuery = useQuery({ queryKey: ['cash-flow-categories'], queryFn: getCashFlowCategories })
  const statusQuery = useQuery({ queryKey: ['cash-flow-status'], queryFn: getCashFlowStatus })

  const drilldownQuery = useQuery({
    queryKey: ['cash-flow-drilldown', drilldown?.categoryId, drilldown?.month, dateFrom, dateTo],
    queryFn: () => getCashFlowTransactions(drilldown!.categoryId, drilldown!.month, dateFrom, dateTo),
    enabled: drilldown != null,
  })

  const refreshMutation = useMutation({
    mutationFn: () => refreshCashFlow(dateFrom, dateTo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cash-flow-matrix'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-status'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-drilldown'] })
    },
  })

  const categorizeMutation = useMutation({
    mutationFn: (body: {
      fingerprint: string
      categoryId: string
      scope: CategorizeScope
      counterpartyInn?: string
      counterpartyAccount?: string
      counterparty?: string
    }) => categorizeTransaction(body),
    onSuccess: () => {
      setPendingChange(null)
      queryClient.invalidateQueries({ queryKey: ['cash-flow-matrix'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-drilldown'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-rules'] })
    },
  })

  const matrix = matrixQuery.data
  const categories = categoriesQuery.data ?? []
  const months = matrix?.months ?? []
  const loadError = matrixQuery.error instanceof Error ? matrixQuery.error : null
  const actionError =
    (refreshMutation.error instanceof Error ? refreshMutation.error : null) ??
    (categorizeMutation.error instanceof Error ? categorizeMutation.error : null)

  function toggle(set: Set<string>, key: string): Set<string> {
    const next = new Set(set)
    if (next.has(key)) {
      next.delete(key)
    } else {
      next.add(key)
    }
    return next
  }

  function onSelectCategory(txn: CashFlowTransaction, categoryId: string) {
    if (!categoryId || categoryId === txn.categoryId) {
      return
    }
    setPendingChange({ txn, categoryId })
  }

  const colCount = months.length + 2

  return (
    <main className="mx-auto max-w-[1600px] space-y-4 overflow-x-hidden text-xs sm:text-[13px]">
      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="grid gap-4 lg:grid-cols-[1fr_auto] lg:items-end">
          <div className="min-w-0">
            <p className="text-[11px] font-black uppercase tracking-[0.22em] text-cyan-700">Cash Flow</p>
            <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">{env.cashFlowTitle}</h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{env.cashFlowInfo}</p>
          </div>
          <div className="grid gap-2 sm:grid-cols-[150px_150px_auto_auto_auto] sm:items-end">
            <label className="grid gap-1 font-bold text-slate-600">
              {env.cashFlowDateFromLabel}
              <input
                type="date"
                value={dateFrom}
                onChange={(event) => setDateFrom(event.target.value)}
                className="h-10 rounded-lg border border-slate-200 bg-white px-3 font-semibold text-slate-950 focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
              />
            </label>
            <label className="grid gap-1 font-bold text-slate-600">
              {env.cashFlowDateToLabel}
              <input
                type="date"
                value={dateTo}
                onChange={(event) => setDateTo(event.target.value)}
                className="h-10 rounded-lg border border-slate-200 bg-white px-3 font-semibold text-slate-950 focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
              />
            </label>
            <button
              type="button"
              onClick={() => refreshMutation.mutate()}
              disabled={refreshMutation.isPending}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-slate-950 px-4 font-black text-white transition hover:bg-cyan-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 disabled:cursor-wait disabled:opacity-70"
            >
              <RefreshCcw className={`h-4 w-4 ${refreshMutation.isPending ? 'animate-spin' : ''}`} aria-hidden="true" />
              {refreshMutation.isPending ? env.cashFlowRefreshingLabel : env.cashFlowRefreshLabel}
            </button>
            <button
              type="button"
              onClick={() => setCategoriesOpen(true)}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 px-4 font-black text-slate-700 transition hover:bg-slate-50"
            >
              <FolderTree className="h-4 w-4" aria-hidden="true" />
              {env.cashFlowCategoriesLabel}
            </button>
            <button
              type="button"
              onClick={() => setMappingOpen(true)}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 px-4 font-black text-slate-700 transition hover:bg-slate-50"
            >
              <ListChecks className="h-4 w-4" aria-hidden="true" />
              {env.cashFlowMappingSheetLabel}
            </button>
          </div>
        </div>
        {statusQuery.data?.lastRefreshAt ? (
          <p className="mt-3 text-[11px] font-semibold text-slate-500">
            {env.cashFlowLastRefreshLabel}: {new Date(statusQuery.data.lastRefreshAt).toLocaleString()}
          </p>
        ) : null}
      </section>

      {loadError || actionError ? (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-red-800">
          <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0" aria-hidden="true" />
          <p className="text-sm font-semibold">{(actionError ?? loadError)?.message}</p>
        </div>
      ) : null}

      {matrix && matrix.sections.length > 0 ? <CashFlowCharts matrix={matrix} /> : null}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-[900px] w-full text-left text-xs">
            <thead className="bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
              <tr>
                <th className="sticky left-0 z-10 bg-slate-50 px-3 py-3">{env.cashFlowCategoryColumnLabel}</th>
                <th className="px-3 py-3 text-right">{env.cashFlowTotalLabel}</th>
                {months.map((month) => (
                  <th key={month} className="px-3 py-3 text-right">{month}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {matrixQuery.isLoading ? (
                <tr><td className="px-4 py-6 text-slate-500" colSpan={colCount}>...</td></tr>
              ) : null}
              {matrix?.sections.map((section) => {
                const sectionOpen = openSections.has(section.sectionKey)
                return (
                  <Fragment key={section.sectionKey}>
                    <tr className="bg-slate-100/70 font-black text-slate-950">
                      <td className="sticky left-0 z-10 bg-slate-100/70 px-3 py-2">
                        <button
                          type="button"
                          className="inline-flex items-center gap-2"
                          onClick={() => setOpenSections((prev) => toggle(prev, section.sectionKey))}
                        >
                          {sectionOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                          {section.nameKa}
                        </button>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">{cell(section.total)}</td>
                      {months.map((month) => (
                        <td key={month} className="px-3 py-2 text-right tabular-nums">{cell(section.monthly[month] ?? 0)}</td>
                      ))}
                    </tr>
                    {sectionOpen && section.directions.map((direction) => {
                      const dirKey = `${section.sectionKey}|${direction.direction}`
                      const dirOpen = openDirections.has(dirKey)
                      return (
                        <Fragment key={dirKey}>
                          <tr className="bg-white font-bold text-slate-800">
                            <td className="sticky left-0 z-10 bg-white px-3 py-2 pl-6">
                              <button
                                type="button"
                                className="inline-flex items-center gap-2"
                                onClick={() => setOpenDirections((prev) => toggle(prev, dirKey))}
                              >
                                {dirOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                                {direction.nameKa}
                              </button>
                            </td>
                            <td className="px-3 py-2 text-right tabular-nums">{cell(direction.total)}</td>
                            {months.map((month) => (
                              <td key={month} className="px-3 py-2 text-right tabular-nums">{cell(direction.monthly[month] ?? 0)}</td>
                            ))}
                          </tr>
                          {dirOpen && direction.categories.map((category) => (
                            <MatrixCategoryRow
                              key={category.categoryId}
                              category={category}
                              months={months}
                              depth={0}
                              openCategories={openCategories}
                              onToggle={(id) => setOpenCategories((prev) => toggle(prev, id))}
                              onDrill={(cat, month) => setDrilldown({ categoryId: cat.categoryId, categoryNameKa: cat.nameKa, month })}
                            />
                          ))}
                        </Fragment>
                      )
                    })}
                  </Fragment>
                )
              })}
              {!matrixQuery.isLoading && (matrix?.sections.length ?? 0) === 0 ? (
                <tr><td className="px-4 py-6 text-slate-500" colSpan={colCount}>{env.cashFlowNoDataLabel}</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      {drilldown ? (
        <DrilldownPanel
          title={`${env.cashFlowDrilldownTitle}: ${drilldown.categoryNameKa}${drilldown.month ? ` · ${drilldown.month}` : ''}`}
          loading={drilldownQuery.isLoading}
          error={drilldownQuery.error instanceof Error ? drilldownQuery.error : null}
          transactions={drilldownQuery.data?.transactions ?? []}
          categories={categories}
          onClose={() => setDrilldown(null)}
          onSelectCategory={onSelectCategory}
        />
      ) : null}

      <ChoiceDialog
        open={pendingChange != null}
        title={env.cashFlowCascadeTitle}
        message={env.cashFlowCascadeQuestion}
        busy={categorizeMutation.isPending}
        cancelLabel={env.cashFlowCancelLabel}
        onCancel={() => setPendingChange(null)}
        options={
          pendingChange
            ? [
                {
                  label: env.cashFlowApplyAllLabel,
                  info: env.cashFlowApplyAllInfo,
                  onSelect: () =>
                    categorizeMutation.mutate({
                      fingerprint: pendingChange.txn.fingerprint,
                      categoryId: pendingChange.categoryId,
                      scope: 'CASCADE',
                      counterpartyInn: pendingChange.txn.counterpartyInn,
                      counterpartyAccount: pendingChange.txn.counterpartyAccount,
                      counterparty: pendingChange.txn.counterparty,
                    }),
                },
                {
                  label: env.cashFlowApplyOneLabel,
                  info: env.cashFlowApplyOneInfo,
                  onSelect: () =>
                    categorizeMutation.mutate({
                      fingerprint: pendingChange.txn.fingerprint,
                      categoryId: pendingChange.categoryId,
                      scope: 'SINGLE',
                    }),
                },
              ]
            : []
        }
      />

      <MappingSheet open={mappingOpen} onClose={() => setMappingOpen(false)} categories={categories} />
      <CategoriesSheet open={categoriesOpen} onClose={() => setCategoriesOpen(false)} categories={categories} />
    </main>
  )
}

function CategoriesSheet({
  open,
  onClose,
  categories,
}: {
  open: boolean
  onClose: () => void
  categories: CashFlowCategory[]
}) {
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<'TOP' | 'SUB'>('TOP')
  const [sectionKey, setSectionKey] = useState<CashFlowSectionKey>('OPERATING')
  const [direction, setDirection] = useState<'INFLOW' | 'OUTFLOW'>('OUTFLOW')
  const [parentId, setParentId] = useState('')
  const [nameKa, setNameKa] = useState('')

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['cash-flow-categories'] })
    queryClient.invalidateQueries({ queryKey: ['cash-flow-matrix'] })
  }

  const addMutation = useMutation({
    mutationFn: () =>
      mode === 'SUB'
        ? createCashFlowCategory({ parentId, nameKa: nameKa.trim() })
        : createCashFlowCategory({ section: sectionKey, direction, nameKa: nameKa.trim() }),
    onSuccess: () => {
      setNameKa('')
      invalidate()
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCashFlowCategory(id),
    onSuccess: invalidate,
  })

  const addError = addMutation.error instanceof Error ? addMutation.error : null
  const deleteError = deleteMutation.error instanceof Error ? deleteMutation.error : null
  const topLevel = categories.filter((category) => !category.parentId)

  return (
    <Drawer open={open} title={env.cashFlowCategoriesTitle} onClose={onClose}>
      <div className="space-y-3 rounded-xl border border-slate-200 p-3">
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setMode('TOP')}
            className={`flex-1 rounded-lg px-2 py-1.5 text-xs font-black ${mode === 'TOP' ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            {env.cashFlowAddCategoryLabel}
          </button>
          <button
            type="button"
            onClick={() => setMode('SUB')}
            className={`flex-1 rounded-lg px-2 py-1.5 text-xs font-black ${mode === 'SUB' ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            {env.cashFlowAddSubcategoryLabel}
          </button>
        </div>

        {mode === 'TOP' ? (
          <div className="grid grid-cols-2 gap-2">
            <label className="grid gap-1 text-[11px] font-bold text-slate-600">
              {env.cashFlowSectionLabel}
              <select value={sectionKey} onChange={(e) => setSectionKey(e.target.value as CashFlowSectionKey)} className="h-9 rounded-lg border border-slate-200 px-2 text-xs">
                {SECTION_OPTIONS.map((option) => (
                  <option key={option.key} value={option.key}>{option.nameKa}</option>
                ))}
              </select>
            </label>
            <label className="grid gap-1 text-[11px] font-bold text-slate-600">
              {env.cashFlowDirectionLabel}
              <select value={direction} onChange={(e) => setDirection(e.target.value as 'INFLOW' | 'OUTFLOW')} className="h-9 rounded-lg border border-slate-200 px-2 text-xs">
                <option value="INFLOW">{env.cashFlowInflowLabel}</option>
                <option value="OUTFLOW">{env.cashFlowOutflowLabel}</option>
              </select>
            </label>
          </div>
        ) : (
          <label className="grid gap-1 text-[11px] font-bold text-slate-600">
            {env.cashFlowParentLabel}
            <select value={parentId} onChange={(e) => setParentId(e.target.value)} className="h-9 rounded-lg border border-slate-200 px-2 text-xs">
              <option value="">{env.cashFlowSelectCategoryLabel}</option>
              {topLevel.map((category) => (
                <option key={category.id} value={category.id}>{category.directionNameKa} · {category.nameKa}</option>
              ))}
            </select>
          </label>
        )}

        <label className="grid gap-1 text-[11px] font-bold text-slate-600">
          {env.cashFlowCategoryNameLabel}
          <input value={nameKa} onChange={(e) => setNameKa(e.target.value)} className="h-9 rounded-lg border border-slate-200 px-2 text-xs" />
        </label>
        {addError ? <p className="text-[11px] font-semibold text-red-600">{addError.message}</p> : null}
        <button
          type="button"
          disabled={!nameKa.trim() || (mode === 'SUB' && !parentId) || addMutation.isPending}
          onClick={() => addMutation.mutate()}
          className="w-full rounded-lg bg-cyan-700 px-3 py-2 text-xs font-black text-white transition hover:bg-cyan-800 disabled:opacity-50"
        >
          {env.cashFlowAddLabel}
        </button>
      </div>

      {deleteError ? <p className="mt-3 text-[11px] font-semibold text-red-600">{deleteError.message}</p> : null}
      <div className="mt-4 space-y-1">
        {topLevel.map((parent) => (
          <div key={parent.id}>
            <CategoryRow category={parent} onDelete={(id) => deleteMutation.mutate(id)} />
            {categories.filter((c) => c.parentId === parent.id).map((child) => (
              <div key={child.id} className="pl-5">
                <CategoryRow category={child} onDelete={(id) => deleteMutation.mutate(id)} />
              </div>
            ))}
          </div>
        ))}
      </div>
    </Drawer>
  )
}

function CategoryRow({ category, onDelete }: { category: CashFlowCategory; onDelete: (id: string) => void }) {
  return (
    <div className="flex items-center justify-between gap-2 rounded-lg border border-slate-100 px-2.5 py-1.5">
      <span className="truncate text-xs text-slate-800">
        {category.nameKa}
        {category.parentId ? null : <span className="ml-1 text-[10px] text-slate-400">{category.directionNameKa}</span>}
      </span>
      {category.builtin ? (
        <span className="text-[10px] text-slate-300">•</span>
      ) : (
        <button type="button" onClick={() => onDelete(category.id)} className="rounded p-1 text-red-500 hover:bg-red-50" aria-label="delete">
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  )
}

function compactGel(value: number) {
  const abs = Math.abs(value)
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (abs >= 1_000) return `${Math.round(value / 1_000)}K`
  return `${Math.round(value)}`
}

/**
 * Combo chart (golden path): expenses as a STACKED bar (segments = expense types,
 * so the stack height is total expenses and the breakdown is visible within), the
 * sales income as a line, and net cash (all inflows − all outflows) as a line.
 * All three requested series in one legible view over the monthly axis.
 */
function CashFlowCharts({ matrix }: { matrix: CashFlowMatrix }) {
  const months = matrix.months
  if (months.length === 0) {
    return null
  }

  const outflowCategories = matrix.sections.flatMap((section) =>
    section.directions.filter((direction) => direction.direction === 'OUTFLOW').flatMap((direction) => direction.categories))
  const sortedExpenses = [...outflowCategories].sort((left, right) => right.total - left.total)
  const topExpenses = sortedExpenses.slice(0, TOP_EXPENSES)
  const otherExpenses = sortedExpenses.slice(TOP_EXPENSES)
  const hasOther = otherExpenses.length > 0

  const salesCategory = matrix.sections
    .flatMap((section) => section.directions.filter((direction) => direction.direction === 'INFLOW').flatMap((direction) => direction.categories))
    .find((category) => category.categoryId === 'sales')

  const monthTotal = (month: string, dir: 'INFLOW' | 'OUTFLOW') =>
    matrix.sections.reduce((sum, section) =>
      sum + section.directions.filter((direction) => direction.direction === dir)
        .reduce((acc, direction) => acc + (direction.monthly[month] ?? 0), 0), 0)

  const data = months.map((month) => {
    const row: Record<string, number | string> = { month }
    topExpenses.forEach((category) => {
      row[category.categoryId] = category.monthly[month] ?? 0
    })
    if (hasOther) {
      row.__other = otherExpenses.reduce((sum, category) => sum + (category.monthly[month] ?? 0), 0)
    }
    row.__sales = salesCategory?.monthly[month] ?? 0
    row.__net = monthTotal(month, 'INFLOW') - monthTotal(month, 'OUTFLOW')
    return row
  })

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 text-base font-black text-slate-950">{env.cashFlowChartTitle}</h2>
      <ResponsiveContainer width="100%" height={340}>
        <ComposedChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
          <XAxis dataKey="month" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={compactGel} width={54} />
          <Tooltip formatter={(value: number, name: string) => [formatGel(value), name]} />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <ReferenceLine y={0} stroke="#94a3b8" />
          {topExpenses.map((category, index) => (
            <Bar
              key={category.categoryId}
              dataKey={category.categoryId}
              stackId="expenses"
              name={category.nameKa}
              fill={EXPENSE_COLORS[index % EXPENSE_COLORS.length]}
            />
          ))}
          {hasOther ? <Bar dataKey="__other" stackId="expenses" name={env.cashFlowChartOtherLabel} fill={OTHER_COLOR} /> : null}
          <Line type="monotone" dataKey="__sales" name={env.cashFlowChartSalesLabel} stroke={SALES_COLOR} strokeWidth={2.5} dot={false} />
          <Line type="monotone" dataKey="__net" name={env.cashFlowChartNetLabel} stroke={NET_COLOR} strokeWidth={2.5} dot={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </section>
  )
}

function MatrixCategoryRow({
  category,
  months,
  depth,
  openCategories,
  onToggle,
  onDrill,
}: {
  category: CashFlowMatrixCategory
  months: string[]
  depth: number
  openCategories: Set<string>
  onToggle: (id: string) => void
  onDrill: (category: CashFlowMatrixCategory, month: string | null) => void
}) {
  const hasChildren = category.children.length > 0
  const open = openCategories.has(category.categoryId)
  const pad = depth === 0 ? 'pl-12' : 'pl-20'
  return (
    <>
      <tr className="text-slate-700 hover:bg-cyan-50/50">
        <td className={`sticky left-0 z-10 bg-white px-3 py-2 ${pad}`}>
          <span className="inline-flex items-center gap-1">
            {hasChildren ? (
              <button type="button" onClick={() => onToggle(category.categoryId)} className="text-slate-400 hover:text-slate-700">
                {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
              </button>
            ) : (
              <span className="inline-block w-3.5" />
            )}
            <button
              type="button"
              className={`text-left hover:text-cyan-700 hover:underline ${hasChildren ? 'font-semibold' : ''}`}
              onClick={() => onDrill(category, null)}
            >
              {category.nameKa}
            </button>
          </span>
        </td>
        <td className="px-3 py-2 text-right tabular-nums font-semibold">
          <button type="button" className="hover:text-cyan-700 hover:underline" onClick={() => onDrill(category, null)}>
            {cell(category.total)}
          </button>
        </td>
        {months.map((month) => {
          const value = category.monthly[month] ?? 0
          return (
            <td key={month} className="px-3 py-2 text-right tabular-nums">
              {value === 0 ? (
                '—'
              ) : (
                <button type="button" className="hover:text-cyan-700 hover:underline" onClick={() => onDrill(category, month)}>
                  {formatGel(value)}
                </button>
              )}
            </td>
          )
        })}
      </tr>
      {hasChildren && open && category.children.map((child) => (
        <MatrixCategoryRow
          key={child.categoryId}
          category={child}
          months={months}
          depth={depth + 1}
          openCategories={openCategories}
          onToggle={onToggle}
          onDrill={onDrill}
        />
      ))}
    </>
  )
}

function DrilldownPanel({
  title,
  loading,
  error,
  transactions,
  categories,
  onClose,
  onSelectCategory,
}: {
  title: string
  loading: boolean
  error: Error | null
  transactions: CashFlowTransaction[]
  categories: CashFlowCategory[]
  onClose: () => void
  onSelectCategory: (txn: CashFlowTransaction, categoryId: string) => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-8">
      <section className="w-full max-w-5xl rounded-2xl border border-slate-200 bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-100 p-4">
          <h2 className="text-base font-black text-slate-950">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-lg px-2 py-1 text-sm font-black text-slate-500 hover:bg-slate-100 hover:text-slate-800">✕</button>
        </div>
        {error ? (
          <div className="m-4 flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-red-800">
            <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
            <p className="text-xs font-semibold">{error.message}</p>
          </div>
        ) : null}
        <div className="max-h-[70vh] overflow-auto">
          <table className="min-w-[820px] w-full text-left text-xs">
            <thead className="sticky top-0 bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2">{env.cashFlowColDateLabel}</th>
                <th className="px-3 py-2">{env.cashFlowColSourceLabel}</th>
                <th className="px-3 py-2">{env.cashFlowColCounterpartyLabel}</th>
                <th className="px-3 py-2 text-right">{env.cashFlowColAmountLabel}</th>
                <th className="px-3 py-2">{env.cashFlowColCategoryLabel}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td className="px-3 py-5 text-slate-500" colSpan={5}>{env.cashFlowRefreshingLabel}</td></tr>
              ) : null}
              {transactions.map((txn) => (
              <tr key={txn.fingerprint} className="text-slate-700">
                <td className="px-3 py-2">{txn.date}</td>
                <td className="px-3 py-2">{txn.source}</td>
                <td className="px-3 py-2">
                  <span className="font-semibold text-slate-900">{txn.counterparty || '—'}</span>
                  {txn.counterpartyInn ? <span className="ml-1 text-[11px] text-slate-400">{txn.counterpartyInn}</span> : null}
                  {txn.description ? <span className="block text-[11px] text-slate-400">{txn.description}</span> : null}
                </td>
                <td className="px-3 py-2 text-right tabular-nums font-semibold">{formatGel(txn.amount)}</td>
                <td className="px-3 py-2">
                  <select
                    value={txn.categoryId}
                    onChange={(event) => onSelectCategory(txn, event.target.value)}
                    className="w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs focus-visible:border-cyan-500 focus-visible:outline-none"
                  >
                    {categoryOptionsForDirection(categories, directionOf(txn)).map((option) => (
                      <option key={option.id} value={option.id}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </td>
              </tr>
            ))}
              {!loading && !error && transactions.length === 0 ? (
                <tr><td className="px-3 py-5 text-slate-500" colSpan={5}>{env.cashFlowNoDataLabel}</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

function MappingSheet({
  open,
  onClose,
  categories,
}: {
  open: boolean
  onClose: () => void
  categories: CashFlowCategory[]
}) {
  const queryClient = useQueryClient()
  const [matchType, setMatchType] = useState<CashFlowMatchType>('TAX_ID')
  const [matchValue, setMatchValue] = useState('')
  const [categoryId, setCategoryId] = useState('')

  const rulesQuery = useQuery({ queryKey: ['cash-flow-rules'], queryFn: getCashFlowRules, enabled: open })

  const addMutation = useMutation({
    mutationFn: () => upsertCashFlowRule({ matchType, matchValue: matchValue.trim(), categoryId }),
    onSuccess: () => {
      setMatchValue('')
      queryClient.invalidateQueries({ queryKey: ['cash-flow-rules'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-matrix'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-drilldown'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCashFlowRule(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cash-flow-rules'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-matrix'] })
      queryClient.invalidateQueries({ queryKey: ['cash-flow-drilldown'] })
    },
  })

  const addError = addMutation.error instanceof Error ? addMutation.error : null

  return (
    <Drawer open={open} title={env.cashFlowMappingSheetTitle} onClose={onClose}>
      <p className="text-xs text-slate-500">{env.cashFlowMappingSheetInfo}</p>

      <div className="mt-4 space-y-2 rounded-xl border border-slate-200 p-3">
        <div className="grid grid-cols-2 gap-2">
          <label className="grid gap-1 text-[11px] font-bold text-slate-600">
            {env.cashFlowRuleMatchTypeLabel}
            <select
              value={matchType}
              onChange={(event) => setMatchType(event.target.value as CashFlowMatchType)}
              className="h-9 rounded-lg border border-slate-200 px-2 text-xs"
            >
              <option value="TAX_ID">TAX_ID</option>
              <option value="IBAN">IBAN</option>
              <option value="NAME">NAME</option>
            </select>
          </label>
          <label className="grid gap-1 text-[11px] font-bold text-slate-600">
            {env.cashFlowRuleMatchValueLabel}
            <input
              value={matchValue}
              onChange={(event) => setMatchValue(event.target.value)}
              className="h-9 rounded-lg border border-slate-200 px-2 text-xs"
            />
          </label>
        </div>
        <label className="grid gap-1 text-[11px] font-bold text-slate-600">
          {env.cashFlowColCategoryLabel}
          <select
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
            className="h-9 rounded-lg border border-slate-200 px-2 text-xs"
          >
            <option value="">{env.cashFlowSelectCategoryLabel}</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>{category.directionNameKa} · {category.nameKa}</option>
            ))}
          </select>
        </label>
        {addError ? <p className="text-[11px] font-semibold text-red-600">{addError.message}</p> : null}
        <button
          type="button"
          disabled={!matchValue.trim() || !categoryId || addMutation.isPending}
          onClick={() => addMutation.mutate()}
          className="w-full rounded-lg bg-cyan-700 px-3 py-2 text-xs font-black text-white transition hover:bg-cyan-800 disabled:opacity-50"
        >
          {env.cashFlowRuleAddLabel}
        </button>
      </div>

      <div className="mt-4 space-y-2">
        {(rulesQuery.data ?? []).map((rule: CashFlowRule) => (
          <div key={rule.id} className="flex items-start justify-between gap-2 rounded-lg border border-slate-200 p-2.5">
            <div className="min-w-0">
              <p className="truncate text-xs font-black text-slate-900">{rule.categoryNameKa}</p>
              <p className="truncate text-[11px] text-slate-500">
                <span className="font-semibold">{rule.matchType}</span> · {rule.matchValue}
                {rule.direction ? ` · ${rule.direction}` : ''}
              </p>
            </div>
            <button
              type="button"
              onClick={() => deleteMutation.mutate(rule.id)}
              className="rounded-lg p-1.5 text-red-500 transition hover:bg-red-50"
              aria-label={env.cashFlowRuleDeleteLabel}
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        ))}
        {rulesQuery.data && rulesQuery.data.length === 0 ? (
          <p className="text-xs text-slate-500">—</p>
        ) : null}
      </div>
    </Drawer>
  )
}
