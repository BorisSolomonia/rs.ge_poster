import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ChevronDown, ChevronRight, RefreshCcw, Search } from 'lucide-react'
import {
  getSupplierCreditors,
  setSupplierCreditorActive,
  syncAllSupplierCreditors,
  syncSupplierCreditor,
} from '../api/supplier-debts.api'
import { formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type { SupplierCreditorOverview, SupplierCreditorRow } from '../types'

const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

function formatLocalDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function today() {
  return formatLocalDate(new Date())
}

const DEFAULT_DATE_FROM = '2025-01-01'

function parseApiDate(value: string | null | undefined) {
  if (!value) {
    return null
  }
  const parsed = new Date(value)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed
  }
  const normalized = value.replace(/(\.\d{3})\d+/, '$1')
  const fallback = new Date(normalized)
  return Number.isNaN(fallback.getTime()) ? null : fallback
}

function formatDateTime(value: string | null | undefined) {
  const date = parseApiDate(value)
  return date ? dateTimeFormatter.format(date) : '-'
}

function formatDate(value: string | null | undefined) {
  const date = parseApiDate(value)
  return date ? dateFormatter.format(date) : '-'
}

function safeText(value: unknown) {
  return typeof value === 'string' ? value : String(value ?? '')
}

function matchesSupplierFilter(supplier: SupplierCreditorRow, query: string) {
  const normalizedQuery = query.trim().toLowerCase()
  if (!normalizedQuery) {
    return true
  }
  return [supplier.supplierName, supplier.supplierTin, supplier.supplierKey]
    .map(safeText)
    .filter(Boolean)
    .some((value) => value.toLowerCase().includes(normalizedQuery))
}

function sortCreditors(rows: SupplierCreditorRow[]) {
  return [...rows].sort((left, right) => {
    if (left.active !== right.active) {
      return left.active ? -1 : 1
    }
    if (left.synced !== right.synced) {
      return left.synced ? -1 : 1
    }
    if (left.debtLeft !== right.debtLeft) {
      return right.debtLeft - left.debtLeft
    }
    return safeText(left.supplierName).localeCompare(safeText(right.supplierName))
  })
}

function withUpdatedRow(current: SupplierCreditorOverview | undefined, row: SupplierCreditorRow) {
  if (!current) {
    return current
  }
  const suppliers = sortCreditors(current.suppliers.map((supplier) => (
    supplier.supplierKey === row.supplierKey ? row : supplier
  )))
  return {
    ...current,
    suppliers,
    syncedSupplierCount: suppliers.filter((supplier) => supplier.synced).length,
    approximateDebtTotal: suppliers
      .filter((supplier) => supplier.synced)
      .reduce((total, supplier) => total + supplier.debtLeft, 0),
  }
}

export default function SupplierDebtsPage() {
  const queryClient = useQueryClient()
  const [dateFrom, setDateFrom] = useState(DEFAULT_DATE_FROM)
  const [dateTo, setDateTo] = useState(today)
  const [supplierFilter, setSupplierFilter] = useState('')
  const [expandedSupplier, setExpandedSupplier] = useState<string | null>(null)
  const queryKey = ['supplier-creditors', dateFrom, dateTo] as const

  const creditorsQuery = useQuery({
    queryKey,
    queryFn: () => getSupplierCreditors(dateFrom, dateTo),
  })

  const syncMutation = useMutation({
    mutationFn: (supplierKey: string) => syncSupplierCreditor(supplierKey, dateFrom, dateTo),
    onSuccess: (row) => {
      queryClient.setQueryData<SupplierCreditorOverview>(queryKey, (current) => withUpdatedRow(current, row))
    },
  })

  const syncAllMutation = useMutation({
    mutationFn: () => syncAllSupplierCreditors(dateFrom, dateTo),
    onSuccess: (overview) => {
      queryClient.setQueryData(queryKey, overview)
    },
  })

  const activeMutation = useMutation({
    mutationFn: ({ supplierKey, active }: { supplierKey: string; active: boolean }) =>
      setSupplierCreditorActive(supplierKey, active, dateFrom, dateTo),
    onSuccess: (overview) => {
      queryClient.setQueryData(queryKey, overview)
    },
  })

  const overview = creditorsQuery.data
  const suppliers = useMemo(
    () => sortCreditors((overview?.suppliers ?? []).filter((supplier) => matchesSupplierFilter(supplier, supplierFilter))),
    [overview?.suppliers, supplierFilter],
  )
  const syncingSupplierKey = syncMutation.variables ?? null
  const loadError = creditorsQuery.error instanceof Error ? creditorsQuery.error : null
  const syncError = syncMutation.error instanceof Error
    ? syncMutation.error
    : syncAllMutation.error instanceof Error ? syncAllMutation.error : null

  return (
    <main className="mx-auto max-w-[1500px] space-y-4 overflow-x-hidden text-xs sm:text-[13px]">
      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="grid gap-4 lg:grid-cols-[1fr_auto] lg:items-end">
          <div className="min-w-0">
            <p className="text-[11px] font-black uppercase tracking-[0.22em] text-cyan-700">Supplier Ledger Control Room</p>
            <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">{env.supplierDebtsTitle}</h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
              Suppliers load without calculating debt. Click a supplier row button to fetch fresh RS.ge and bank data, calculate that supplier, and save the result for this date range.
            </p>
          </div>
          <div className="grid gap-2 sm:grid-cols-[160px_160px_280px_auto] sm:items-end">
            <label className="grid gap-1 font-bold text-slate-600">
              Date From
              <input
                type="date"
                value={dateFrom}
                onChange={(event) => setDateFrom(event.target.value)}
                className="h-10 rounded-lg border border-slate-200 bg-white px-3 font-semibold text-slate-950 focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
              />
            </label>
            <label className="grid gap-1 font-bold text-slate-600">
              Date To
              <input
                type="date"
                value={dateTo}
                onChange={(event) => setDateTo(event.target.value)}
                className="h-10 rounded-lg border border-slate-200 bg-white px-3 font-semibold text-slate-950 focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
              />
            </label>
            <label className="relative grid gap-1 font-bold text-slate-600">
              Search
              <Search className="pointer-events-none absolute bottom-3 left-3 h-4 w-4 text-slate-400" aria-hidden="true" />
              <input
                type="search"
                value={supplierFilter}
                onChange={(event) => setSupplierFilter(event.target.value)}
                placeholder="Search supplier or TIN"
                className="h-10 rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-3 font-semibold text-slate-950 focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
              />
            </label>
            <button
              type="button"
              onClick={() => syncAllMutation.mutate()}
              disabled={syncAllMutation.isPending || syncMutation.isPending}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-cyan-700 px-4 font-black text-white transition hover:bg-cyan-800 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 disabled:cursor-wait disabled:opacity-70"
            >
              <RefreshCcw className={`h-4 w-4 ${syncAllMutation.isPending ? 'animate-spin' : ''}`} aria-hidden="true" />
              {syncAllMutation.isPending ? 'Syncing All...' : 'Sync All'}
            </button>
          </div>
        </div>
      </section>

      <section className="grid gap-3 md:grid-cols-3">
        <SummaryCard label="Approximate Total Debt" value={formatGel(overview?.approximateDebtTotal ?? 0)} />
        <SummaryCard label="Synced Suppliers" value={`${overview?.syncedSupplierCount ?? 0} / ${overview?.totalSupplierCount ?? 0}`} />
        <SummaryCard label="Listed Suppliers" value={`${suppliers.length}`} />
      </section>

      {loadError || syncError ? (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-red-800">
          <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0" aria-hidden="true" />
          <div>
            <p className="font-black">Creditor data problem</p>
            <p className="mt-1 text-sm">{(syncError ?? loadError)?.message}</p>
          </div>
        </div>
      ) : null}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-1 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-black text-slate-950">Creditor Balances</h2>
            <p className="text-slate-500">
              Approximate total uses saved supplier calculations only. Unsynced rows are not counted as zero.
            </p>
          </div>
          {overview ? <p className="text-slate-500">Generated {formatDateTime(overview.generatedAt)}</p> : null}
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-[1180px] w-full text-left text-xs">
            <thead className="bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-3">Use</th>
                <th className="px-3 py-3">Supplier</th>
                <th className="px-3 py-3">TIN</th>
                <th className="px-3 py-3 text-right">Purchases</th>
                <th className="px-3 py-3 text-right">BOG</th>
                <th className="px-3 py-3 text-right">TBC</th>
                <th className="px-3 py-3 text-right">Manual</th>
                <th className="px-3 py-3 text-right">Debt</th>
                <th className="px-3 py-3 text-right">Rows</th>
                <th className="px-3 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {creditorsQuery.isLoading ? (
                <tr>
                  <td className="px-4 py-6 text-slate-500" colSpan={10}>Loading suppliers...</td>
                </tr>
              ) : null}
              {suppliers.map((supplier) => {
                const open = expandedSupplier === supplier.supplierKey
                const isSyncing = syncMutation.isPending && syncingSupplierKey === supplier.supplierKey
                return (
                  <FragmentRow
                    key={supplier.supplierKey}
                    supplier={supplier}
                    open={open}
                    isSyncing={isSyncing}
                    isToggling={activeMutation.isPending && activeMutation.variables?.supplierKey === supplier.supplierKey}
                    onToggleOpen={() => setExpandedSupplier(open ? null : supplier.supplierKey)}
                    onToggleActive={(active) => activeMutation.mutate({ supplierKey: supplier.supplierKey, active })}
                    onSync={() => syncMutation.mutate(supplier.supplierKey)}
                  />
                )
              })}
              {!creditorsQuery.isLoading && suppliers.length === 0 ? (
                <tr>
                  <td className="px-4 py-6 text-slate-500" colSpan={10}>No suppliers match this search and date range.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  )
}

type FragmentRowProps = {
  supplier: SupplierCreditorRow
  open: boolean
  isSyncing: boolean
  isToggling: boolean
  onToggleOpen: () => void
  onToggleActive: (active: boolean) => void
  onSync: () => void
}

function FragmentRow({ supplier, open, isSyncing, isToggling, onToggleOpen, onToggleActive, onSync }: FragmentRowProps) {
  return (
    <>
      <tr className={`${supplier.active ? 'bg-white' : 'bg-slate-50 text-slate-500'} transition-colors hover:bg-cyan-50/70`}>
        <td className="px-3 py-3 align-middle">
          <input
            type="checkbox"
            checked={supplier.active}
            disabled={isToggling}
            onChange={(event) => onToggleActive(event.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-cyan-700 focus:ring-cyan-500"
            aria-label={`Use ${supplier.supplierName} in creditors list priority`}
          />
        </td>
        <td className="px-3 py-3 align-middle font-black text-slate-950">
          <button
            type="button"
            onClick={onToggleOpen}
            className="inline-flex max-w-[360px] items-center gap-2 text-left hover:text-cyan-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
          >
            {open ? <ChevronDown className="h-4 w-4" aria-hidden="true" /> : <ChevronRight className="h-4 w-4" aria-hidden="true" />}
            <span className="truncate">{supplier.supplierName}</span>
          </button>
          {supplier.lastSyncError ? <p className="mt-1 text-[11px] font-semibold text-red-700">{supplier.lastSyncError}</p> : null}
        </td>
        <td className="px-3 py-3 align-middle font-mono text-[11px] text-slate-500">{supplier.supplierTin || '-'}</td>
        <td className="px-3 py-3 text-right align-middle font-semibold tabular-nums">{supplier.synced ? formatGel(supplier.purchaseTotal) : '-'}</td>
        <td className="px-3 py-3 text-right align-middle font-semibold tabular-nums text-emerald-700">{supplier.synced ? formatGel(supplier.bogPaidTotal) : '-'}</td>
        <td className="px-3 py-3 text-right align-middle font-semibold tabular-nums text-cyan-700">{supplier.synced ? formatGel(supplier.tbcPaidTotal) : '-'}</td>
        <td className="px-3 py-3 text-right align-middle font-semibold tabular-nums text-amber-700">{supplier.synced ? formatGel(supplier.cashPaidTotal) : '-'}</td>
        <td className={`px-3 py-3 text-right align-middle font-black tabular-nums ${supplier.debtLeft > 0 ? 'text-red-700' : 'text-emerald-700'}`}>
          {supplier.synced ? formatGel(supplier.debtLeft) : '-'}
        </td>
        <td className="px-3 py-3 text-right align-middle text-slate-500">
          {supplier.synced ? `${supplier.purchaseCount} / ${supplier.paymentCount}` : '-'}
        </td>
        <td className="px-3 py-3 text-right align-middle">
          <button
            type="button"
            onClick={onSync}
            disabled={isSyncing}
            className="inline-flex min-h-10 min-w-[128px] flex-col items-center justify-center rounded-lg bg-slate-950 px-3 py-1.5 font-black text-white transition hover:bg-cyan-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 disabled:cursor-wait disabled:opacity-70"
          >
            <span className="inline-flex items-center gap-1.5">
              <RefreshCcw className={`h-3.5 w-3.5 ${isSyncing ? 'animate-spin' : ''}`} aria-hidden="true" />
              {isSyncing ? 'Calculating' : supplier.synced ? 'Sync Again' : 'Calculate'}
            </span>
            {supplier.lastSyncedAt ? <span className="mt-0.5 text-[10px] font-semibold opacity-80">Last: {formatDateTime(supplier.lastSyncedAt)}</span> : null}
          </button>
        </td>
      </tr>
      {open ? (
        <tr className="bg-slate-50/70">
          <td colSpan={10} className="px-4 py-4">
            <SupplierDetails supplier={supplier} />
          </td>
        </tr>
      ) : null}
    </>
  )
}

function SupplierDetails({ supplier }: { supplier: SupplierCreditorRow }) {
  if (!supplier.synced) {
    return <p className="text-slate-500">No saved calculation yet. Click Calculate to fetch and save this supplier's purchases and payments.</p>
  }
  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <DetailTable
        title="Purchases"
        empty="No purchases saved for this supplier and date range."
        headers={['Date', 'Waybill', 'Amount']}
        rows={(supplier.purchases ?? []).map((purchase) => [formatDate(purchase.date), purchase.waybillNumber || '-', formatGel(purchase.amount)])}
      />
      <DetailTable
        title="Payments"
        empty="No payments saved for this supplier and date range."
        headers={['Date', 'Provider', 'Amount', 'Reference']}
        rows={(supplier.payments ?? []).map((payment) => [formatDate(payment.date), payment.provider, formatGel(payment.amount), payment.reference || payment.description || '-'])}
      />
    </div>
  )
}

function DetailTable({ title, empty, headers, rows }: { title: string; empty: string; headers: string[]; rows: string[][] }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-3 py-2 font-black text-slate-950">{title}</div>
      {rows.length === 0 ? (
        <p className="p-3 text-slate-500">{empty}</p>
      ) : (
        <div className="max-h-72 overflow-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
              <tr>
                {headers.map((header) => <th key={header} className="px-3 py-2">{header}</th>)}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row, index) => (
                <tr key={`${title}-${index}`}>
                  {row.map((cell, cellIndex) => <td key={`${title}-${index}-${cellIndex}`} className="px-3 py-2 text-slate-700">{cell}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-[11px] font-black uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-black text-slate-950 sm:text-2xl">{value}</p>
    </div>
  )
}
