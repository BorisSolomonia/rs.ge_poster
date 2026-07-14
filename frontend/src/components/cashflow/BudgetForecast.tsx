import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, RotateCcw } from 'lucide-react'
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
  clearBudgetOverride,
  getBudgetForecast,
  setBudgetOverride,
} from '../../api/cash-flow.api'
import { formatGel } from '../reconciliation/reconciliation.utils'
import { env } from '../../env'
import type {
  BudgetBasis,
  BudgetForecastCell,
  BudgetForecastPeriod,
  BudgetForecastRow,
  BudgetForecastTotal,
} from '../../types'

const INCOME_COLOR = '#16a34a'
const EXPENSE_COLOR = '#ef4444'
const NET_COLOR = '#0f172a'

const BASIS_LABEL: Record<BudgetBasis, string> = {
  SEASONAL_GROWTH: env.budgetBasisSeasonalLabel,
  TREND: env.budgetBasisTrendLabel,
  AVERAGE: env.budgetBasisAverageLabel,
  NONE: env.budgetBasisNoneLabel,
}

function compact(value: number) {
  const abs = Math.abs(value)
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (abs >= 1_000) return `${Math.round(value / 1_000)}K`
  return `${Math.round(value)}`
}

export default function BudgetForecast() {
  const queryClient = useQueryClient()
  const forecastQuery = useQuery({ queryKey: ['budget-forecast'], queryFn: () => getBudgetForecast() })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['budget-forecast'] })

  const setMutation = useMutation({
    mutationFn: (body: { periodType: string; periodKey: string; categoryId: string; amount: number }) =>
      setBudgetOverride(body),
    onSuccess: invalidate,
  })
  const clearMutation = useMutation({
    mutationFn: (body: { periodType: string; periodKey: string; categoryId: string }) =>
      clearBudgetOverride(body.periodType, body.periodKey, body.categoryId),
    onSuccess: invalidate,
  })

  const forecast = forecastQuery.data
  const loadError = forecastQuery.error instanceof Error ? forecastQuery.error : null
  const actionError =
    (setMutation.error instanceof Error ? setMutation.error : null) ??
    (clearMutation.error instanceof Error ? clearMutation.error : null)

  if (forecastQuery.isLoading) {
    return <p className="rounded-2xl border border-slate-200 bg-white p-6 text-slate-500 shadow-sm">{env.budgetLoadingLabel}</p>
  }
  if (loadError) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-red-800">
        <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0" aria-hidden="true" />
        <p className="text-sm font-semibold">{loadError.message}</p>
      </div>
    )
  }
  if (!forecast || forecast.periods.length === 0 || forecast.rows.length === 0) {
    return <p className="rounded-2xl border border-slate-200 bg-white p-6 text-slate-500 shadow-sm">{env.budgetNoDataLabel}</p>
  }

  const periods = forecast.periods
  const income = forecast.rows.filter((row) => row.direction === 'INFLOW')
  const expense = forecast.rows.filter((row) => row.direction === 'OUTFLOW')
  const totalsByKey = new Map(forecast.totals.map((total) => [total.periodKey, total]))

  const applyOverride = (row: BudgetForecastRow, period: BudgetForecastPeriod, amount: number) =>
    setMutation.mutate({ periodType: period.type, periodKey: period.key, categoryId: row.categoryId, amount })
  const resetOverride = (row: BudgetForecastRow, period: BudgetForecastPeriod) =>
    clearMutation.mutate({ periodType: period.type, periodKey: period.key, categoryId: row.categoryId })

  const busy = setMutation.isPending || clearMutation.isPending

  return (
    <div className="space-y-4">
      <p className="max-w-3xl text-sm leading-6 text-slate-600">{env.budgetInfo}</p>

      {forecast.historyUncategorized ? (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-amber-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
          <p className="text-xs font-semibold">{env.budgetUncategorizedHint}</p>
        </div>
      ) : null}

      {actionError ? (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-3 text-red-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
          <p className="text-xs font-semibold">{actionError.message}</p>
        </div>
      ) : null}

      <ForecastChart periods={periods} totalsByKey={totalsByKey} />

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-[900px] w-full text-left text-xs">
            <thead className="bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
              <tr>
                <th className="sticky left-0 z-10 bg-slate-50 px-3 py-3">{env.budgetCategoryColumnLabel}</th>
                {periods.map((period, index) => {
                  const firstMonth = period.type === 'MONTH' && periods[index - 1]?.type === 'WEEK'
                  return (
                    <th
                      key={period.key}
                      className={`px-3 py-3 text-right ${firstMonth ? 'border-l-2 border-slate-200' : ''}`}
                    >
                      <span className="block">{period.labelKa}</span>
                      <span className="block text-[9px] font-semibold text-slate-400">
                        {period.type === 'WEEK' ? env.budgetWeekGroupLabel : env.budgetMonthGroupLabel}
                      </span>
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <GroupHeader label={env.budgetIncomeLabel} span={periods.length + 1} />
              {income.map((row) => (
                <ForecastRow
                  key={row.categoryId}
                  row={row}
                  periods={periods}
                  busy={busy}
                  onCommit={applyOverride}
                  onReset={resetOverride}
                />
              ))}
              <TotalRow
                label={env.budgetTotalIncomeLabel}
                periods={periods}
                value={(total) => total.inflowAmount}
                tone="income"
                totalsByKey={totalsByKey}
              />

              <GroupHeader label={env.budgetExpenseLabel} span={periods.length + 1} />
              {expense.map((row) => (
                <ForecastRow
                  key={row.categoryId}
                  row={row}
                  periods={periods}
                  busy={busy}
                  onCommit={applyOverride}
                  onReset={resetOverride}
                />
              ))}
              <TotalRow
                label={env.budgetTotalExpenseLabel}
                periods={periods}
                value={(total) => total.outflowAmount}
                tone="expense"
                totalsByKey={totalsByKey}
              />

              <TotalRow
                label={env.budgetNetLabel}
                periods={periods}
                value={(total) => total.netAmount}
                tone="net"
                totalsByKey={totalsByKey}
              />
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

function GroupHeader({ label, span }: { label: string; span: number }) {
  return (
    <tr className="bg-slate-100/70 font-black text-slate-950">
      <td className="sticky left-0 z-10 bg-slate-100/70 px-3 py-2" colSpan={span}>
        {label}
      </td>
    </tr>
  )
}

function ForecastRow({
  row,
  periods,
  busy,
  onCommit,
  onReset,
}: {
  row: BudgetForecastRow
  periods: BudgetForecastPeriod[]
  busy: boolean
  onCommit: (row: BudgetForecastRow, period: BudgetForecastPeriod, amount: number) => void
  onReset: (row: BudgetForecastRow, period: BudgetForecastPeriod) => void
}) {
  const cellsByKey = new Map(row.cells.map((cell) => [cell.periodKey, cell]))
  return (
    <tr className="text-slate-700 hover:bg-cyan-50/40">
      <td className={`sticky left-0 z-10 bg-white px-3 py-1.5 ${row.parentId ? 'pl-8' : 'pl-4 font-semibold'}`}>
        {row.categoryName}
      </td>
      {periods.map((period, index) => {
        const cell = cellsByKey.get(period.key)
        const firstMonth = period.type === 'MONTH' && periods[index - 1]?.type === 'WEEK'
        return (
          <td key={period.key} className={`px-2 py-1.5 text-right ${firstMonth ? 'border-l-2 border-slate-200' : ''}`}>
            {cell ? (
              <EditableCell
                cell={cell}
                busy={busy}
                onCommit={(amount) => onCommit(row, period, amount)}
                onReset={() => onReset(row, period)}
              />
            ) : (
              <span className="text-slate-300">—</span>
            )}
          </td>
        )
      })}
    </tr>
  )
}

function EditableCell({
  cell,
  busy,
  onCommit,
  onReset,
}: {
  cell: BudgetForecastCell
  busy: boolean
  onCommit: (amount: number) => void
  onReset: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')

  function startEditing() {
    setDraft(String(cell.amount ?? 0))
    setEditing(true)
  }

  function commit() {
    setEditing(false)
    const parsed = Number(draft.replace(',', '.'))
    if (!Number.isFinite(parsed)) {
      return
    }
    if (Math.abs(parsed - cell.amount) < 0.005) {
      return // unchanged
    }
    onCommit(parsed)
  }

  if (editing) {
    return (
      <input
        autoFocus
        type="number"
        inputMode="decimal"
        value={draft}
        disabled={busy}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.currentTarget.blur()
          } else if (event.key === 'Escape') {
            setEditing(false)
          }
        }}
        className="w-24 rounded-md border border-cyan-400 px-1.5 py-1 text-right text-xs tabular-nums focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-200"
      />
    )
  }

  const title = cell.overridden
    ? env.budgetEditedLabel
    : `${BASIS_LABEL[cell.basis]} · ${formatGel(cell.baseline)}`
  const display = cell.amount === 0 && !cell.overridden ? '—' : formatGel(cell.amount)

  return (
    <span className="inline-flex items-center justify-end gap-1">
      {cell.overridden ? (
        <button
          type="button"
          onClick={onReset}
          disabled={busy}
          title={`${env.budgetResetLabel} (${formatGel(cell.baseline)})`}
          className="rounded p-0.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
        >
          <RotateCcw className="h-3 w-3" />
        </button>
      ) : null}
      <button
        type="button"
        onClick={startEditing}
        title={title}
        className={`rounded px-1.5 py-1 text-right tabular-nums transition hover:bg-cyan-100/60 ${
          cell.overridden ? 'font-black text-cyan-800 underline decoration-dotted' : 'text-slate-700'
        }`}
      >
        {display}
      </button>
    </span>
  )
}

function TotalRow({
  label,
  periods,
  value,
  tone,
  totalsByKey,
}: {
  label: string
  periods: BudgetForecastPeriod[]
  value: (total: BudgetForecastTotal) => number
  tone: 'income' | 'expense' | 'net'
  totalsByKey: Map<string, BudgetForecastTotal>
}) {
  return (
    <tr className={`font-black ${tone === 'net' ? 'bg-slate-900 text-white' : 'bg-white text-slate-900'}`}>
      <td className={`sticky left-0 z-10 px-3 py-2 ${tone === 'net' ? 'bg-slate-900' : 'bg-white'}`}>{label}</td>
      {periods.map((period, index) => {
        const firstMonth = period.type === 'MONTH' && periods[index - 1]?.type === 'WEEK'
        const total = totalsByKey.get(period.key)
        const amount = total ? value(total) : 0
        const netTone = tone === 'net' ? (amount >= 0 ? 'text-emerald-300' : 'text-red-300') : ''
        return (
          <td
            key={period.key}
            className={`px-3 py-2 text-right tabular-nums ${firstMonth ? 'border-l-2 border-slate-200' : ''} ${netTone}`}
          >
            {amount === 0 ? '—' : formatGel(amount)}
          </td>
        )
      })}
    </tr>
  )
}

function ForecastChart({
  periods,
  totalsByKey,
}: {
  periods: BudgetForecastPeriod[]
  totalsByKey: Map<string, BudgetForecastTotal>
}) {
  const data = periods.map((period) => {
    const total = totalsByKey.get(period.key)
    return {
      label: period.labelKa,
      income: total?.inflowAmount ?? 0,
      expense: total?.outflowAmount ?? 0,
      net: total?.netAmount ?? 0,
    }
  })

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 text-base font-black text-slate-950">{env.budgetChartTitle}</h2>
      <ResponsiveContainer width="100%" height={300}>
        <ComposedChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
          <XAxis dataKey="label" tick={{ fontSize: 10 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={compact} width={54} />
          <Tooltip formatter={(value: number, name: string) => [formatGel(value), name]} />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <ReferenceLine y={0} stroke="#94a3b8" />
          <Bar dataKey="income" name={env.budgetIncomeLabel} fill={INCOME_COLOR} />
          <Bar dataKey="expense" name={env.budgetExpenseLabel} fill={EXPENSE_COLOR} />
          <Line type="monotone" dataKey="net" name={env.budgetNetLabel} stroke={NET_COLOR} strokeWidth={2.5} dot={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </section>
  )
}
