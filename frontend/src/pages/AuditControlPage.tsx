import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CalendarDays, Download, FileWarning, RefreshCcw, ShieldCheck } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { getSupplierDebtOverview, syncSupplierDebtSources } from '../api/supplier-debts.api'
import { formatGel } from '../components/reconciliation/reconciliation.utils'
import type { SupplierDebtOverview, SupplierDebtRow } from '../types'

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

function formatLocalDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function startOfCurrentMonth() {
  const now = new Date()
  return formatLocalDate(new Date(now.getFullYear(), now.getMonth(), 1))
}

function today() {
  return formatLocalDate(new Date())
}

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
  return date ? dateFormatter.format(date) : '-'
}

function csvEscape(value: string | number | null | undefined) {
  const text = String(value ?? '')
  return /[",\n]/.test(text) ? '"' + text.split('"').join('""') + '"' : text
}

function downloadSupplierLedgerCsv(overview: SupplierDebtOverview) {
  const headers = [
    'Supplier Key',
    'TIN',
    'Supplier Name',
    'Purchases',
    'BOG Paid',
    'TBC Paid',
    'Manual Paid',
    'Total Paid',
    'Debt Left',
    'Purchases Count',
    'Payments Count',
  ]
  const rows = (overview.suppliers ?? []).map((supplier) => [
    supplier.supplierKey,
    supplier.supplierTin,
    supplier.supplierName,
    supplier.purchaseTotal,
    supplier.bogPaidTotal,
    supplier.tbcPaidTotal,
    supplier.cashPaidTotal,
    supplier.paidTotal,
    supplier.debtLeft,
    supplier.purchaseCount,
    supplier.paymentCount,
  ])
  const csv = [headers, ...rows].map((row) => row.map(csvEscape).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `audit-control-supplier-ledger-${overview.dateFrom}-${overview.dateTo}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function topDebtSuppliers(suppliers: SupplierDebtRow[]) {
  return [...suppliers]
    .filter((supplier) => supplier.debtLeft > 0)
    .sort((left, right) => right.debtLeft - left.debtLeft)
    .slice(0, 8)
}

export default function AuditControlPage() {
  const queryClient = useQueryClient()
  const [dateFrom, setDateFrom] = useState(startOfCurrentMonth)
  const [dateTo, setDateTo] = useState(today)
  const queryKey = ['audit-control', dateFrom, dateTo] as const

  const overviewQuery = useQuery({
    queryKey,
    queryFn: () => getSupplierDebtOverview(dateFrom, dateTo),
  })

  const syncMutation = useMutation({
    mutationFn: () => syncSupplierDebtSources(dateFrom, dateTo),
    onSuccess: (overview) => {
      queryClient.setQueryData(queryKey, overview)
      queryClient.setQueryData(['supplier-debts', dateFrom, dateTo], overview)
    },
  })

  const overview = overviewQuery.data
  const debtSuppliers = useMemo(() => topDebtSuppliers(overview?.suppliers ?? []), [overview?.suppliers])
  const isSyncing = overviewQuery.isFetching || syncMutation.isPending
  const error = syncMutation.error instanceof Error ? syncMutation.error : overview ? null : overviewQuery.error

  return (
    <main className="mx-auto max-w-[1500px] space-y-4 text-xs sm:text-[13px]">
      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <p className="text-[11px] font-black uppercase tracking-[0.22em] text-cyan-700">Audit Control</p>
            <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">Documented Source Freshness</h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
              Management view for current RS.ge purchase documents, BOG/TBC payments, manual supplier payments, unmatched payment exceptions, and supplier debt exposure.
            </p>
          </div>
          <div className="grid gap-2 sm:grid-cols-[160px_160px_auto_auto] sm:items-end">
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
            <button
              type="button"
              onClick={() => syncMutation.mutate()}
              disabled={isSyncing}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-slate-950 px-4 font-black text-white transition hover:bg-cyan-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 disabled:cursor-wait disabled:opacity-70"
            >
              <RefreshCcw className={`h-4 w-4 ${isSyncing ? 'animate-spin' : ''}`} aria-hidden="true" />
              {syncMutation.isPending ? 'Syncing' : 'Sync Now'}
            </button>
            <button
              type="button"
              onClick={() => overview && downloadSupplierLedgerCsv(overview)}
              disabled={!overview}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-4 font-black text-slate-950 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Download className="h-4 w-4" aria-hidden="true" />
              Export CSV
            </button>
          </div>
        </div>
      </section>

      {error instanceof Error ? (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-red-800">
          <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0" aria-hidden="true" />
          <div>
            <p className="font-black">Audit data could not be loaded</p>
            <p className="mt-1 text-sm">{error.message}</p>
          </div>
        </div>
      ) : null}

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Real Purchase Documents" value={formatGel(overview?.purchaseTotal ?? 0)} icon={ShieldCheck} />
        <MetricCard label="Matched Payments" value={formatGel(overview?.paidTotal ?? 0)} icon={CalendarDays} />
        <MetricCard label="Open Supplier Debt" value={formatGel(overview?.debtTotal ?? 0)} icon={FileWarning} tone={(overview?.debtTotal ?? 0) > 0 ? 'bad' : 'good'} />
        <MetricCard label="Unmatched Payment Exceptions" value={formatGel(overview?.unmatchedPaymentTotal ?? 0)} icon={AlertTriangle} tone={(overview?.unmatchedPaymentTotal ?? 0) > 0 ? 'warn' : 'good'} />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr_420px]">
        <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-100 p-4">
            <h2 className="text-base font-black text-slate-950">Source Freshness</h2>
            <p className="mt-1 text-slate-500">Last completed sync: {formatDateTime(overview?.lastRefreshCompletedAt ?? overview?.snapshotGeneratedAt)}</p>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-[720px] w-full text-left text-xs">
              <thead className="bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Source</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Rows</th>
                  <th className="px-4 py-3 text-right">Total</th>
                  <th className="px-4 py-3">Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {(overview?.sourceStatuses ?? []).map((status) => (
                  <tr key={status.source}>
                    <td className="px-4 py-3 font-black text-slate-950">{status.source}</td>
                    <td className="px-4 py-3 font-semibold text-slate-700">{status.status}</td>
                    <td className="px-4 py-3 text-right font-semibold tabular-nums">{status.recordCount}</td>
                    <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatGel(status.total)}</td>
                    <td className="px-4 py-3 text-slate-500">{status.message || '-'}</td>
                  </tr>
                ))}
                {!overview?.sourceStatuses?.length ? (
                  <tr>
                    <td className="px-4 py-5 text-slate-500" colSpan={5}>No source status is available yet.</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>

        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 shadow-sm">
          <h2 className="flex items-center gap-2 text-base font-black text-amber-950">
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
            Architecture Gate
          </h2>
          <p className="mt-2 text-sm leading-6 text-amber-900">
            Inventory ledger, product parent-child hierarchy, customer real-entity filtering, audit exceptions table, legal write-off calculations, and manual payment override auditing require a durable persistence model. See docs/AUDIT_CONTROL_ARCHITECTURE.md before extending this page.
          </p>
        </div>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-4">
          <h2 className="text-base font-black text-slate-950">Largest Open Supplier Debts</h2>
          <p className="mt-1 text-slate-500">Current supplier debt exposure for the selected period.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-[820px] w-full text-left text-xs">
            <thead className="bg-slate-50 text-[11px] font-black uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Supplier</th>
                <th className="px-4 py-3">TIN</th>
                <th className="px-4 py-3 text-right">Purchases</th>
                <th className="px-4 py-3 text-right">Paid</th>
                <th className="px-4 py-3 text-right">Debt</th>
                <th className="px-4 py-3 text-right">Rows</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {debtSuppliers.map((supplier) => (
                <tr key={supplier.supplierKey}>
                  <td className="px-4 py-3 font-black text-slate-950">{supplier.supplierName}</td>
                  <td className="px-4 py-3 font-mono text-[11px] text-slate-500">{supplier.supplierTin || '-'}</td>
                  <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatGel(supplier.purchaseTotal)}</td>
                  <td className="px-4 py-3 text-right font-semibold tabular-nums text-emerald-700">{formatGel(supplier.paidTotal)}</td>
                  <td className="px-4 py-3 text-right font-black tabular-nums text-red-700">{formatGel(supplier.debtLeft)}</td>
                  <td className="px-4 py-3 text-right text-slate-500">{supplier.purchaseCount} / {supplier.paymentCount}</td>
                </tr>
              ))}
              {debtSuppliers.length === 0 ? (
                <tr>
                  <td className="px-4 py-5 text-slate-500" colSpan={6}>No open supplier debt is available for this period.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  )
}

type MetricCardProps = {
  label: string
  value: string
  icon: LucideIcon
  tone?: 'good' | 'warn' | 'bad'
}

function MetricCard({ label, value, icon: Icon, tone = 'good' }: MetricCardProps) {
  const toneClass = tone === 'bad' ? 'text-red-700 bg-red-50' : tone === 'warn' ? 'text-amber-700 bg-amber-50' : 'text-emerald-700 bg-emerald-50'
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className={`inline-flex h-9 w-9 items-center justify-center rounded-lg ${toneClass}`}>
        <Icon className="h-4 w-4" aria-hidden="true" />
      </div>
      <p className="mt-3 text-[11px] font-black uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-black text-slate-950 sm:text-2xl">{value}</p>
    </div>
  )
}
