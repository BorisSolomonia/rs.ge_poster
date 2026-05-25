import React, { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  Landmark,
  Plus,
  RefreshCcw,
  Save,
  Trash2,
  Wallet,
} from 'lucide-react'
import {
  auditSupplierDebts,
  deleteSupplierCashPayment,
  getSupplierDebtOverview,
  getSupplierDebtSupplierTransactions,
  saveSupplierCashPayment,
  saveSupplierPaymentMapping,
} from '../api/supplier-debts.api'
import { formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type { SupplierDebtAudit, SupplierDebtOverview, SupplierDebtRow, SupplierDebtUnmatchedGroup } from '../types'

const today = () => new Date().toISOString().slice(0, 10)

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

export default function SupplierDebtsPage() {
  const queryClient = useQueryClient()
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState(today())
  const [expandedSupplier, setExpandedSupplier] = useState<string | null>(null)
  const [mappingDrafts, setMappingDrafts] = useState<Record<string, string>>({})
  const [cashForm, setCashForm] = useState({
    supplierKey: '',
    date: today(),
    amount: '',
    note: '',
  })
  const debtQueryKey = ['supplier-debts', dateFrom || 'default-opening-date', dateTo || 'today'] as const

  const debtQuery = useQuery({
    queryKey: debtQueryKey,
    queryFn: () => getSupplierDebtOverview(dateFrom || undefined, dateTo || undefined),
  })

  const supplierDetailsQuery = useQuery({
    queryKey: ['supplier-debt-transactions', expandedSupplier, dateFrom || 'default-opening-date', dateTo || 'today'],
    queryFn: () => getSupplierDebtSupplierTransactions(expandedSupplier ?? '', dateFrom || undefined, dateTo || undefined),
    enabled: Boolean(expandedSupplier),
  })

  const sourceRefreshMutation = useMutation({
    mutationFn: () => getSupplierDebtOverview(dateFrom || undefined, dateTo || undefined, true),
    onSuccess: (data) => {
      queryClient.setQueryData(debtQueryKey, data)
    },
  })

  const auditMutation = useMutation({
    mutationFn: () => auditSupplierDebts(dateFrom || undefined, dateTo || undefined),
    onSuccess: (audit) => {
      queryClient.setQueryData<SupplierDebtOverview>(debtQueryKey, (current) =>
        current ? { ...current, latestAudit: audit } : current,
      )
    },
  })

  const saveMappingMutation = useMutation({
    mutationFn: ({ group, supplier }: { group: SupplierDebtUnmatchedGroup; supplier: SupplierDebtRow }) =>
      saveSupplierPaymentMapping({
        provider: group.provider,
        matchText: group.matchText,
        supplierKey: supplier.supplierKey,
        supplierTin: supplier.supplierTin,
        supplierName: supplier.supplierName,
      }),
    onSuccess: async () => {
      setMappingDrafts({})
      await queryClient.invalidateQueries({ queryKey: ['supplier-debts'] })
      await queryClient.invalidateQueries({ queryKey: ['supplier-debt-transactions'] })
    },
  })

  const saveCashMutation = useMutation({
    mutationFn: () => {
      const supplier = debtQuery.data?.suppliers.find((item) => item.supplierKey === cashForm.supplierKey)
      if (!supplier) {
        throw new Error('Select a supplier before saving a cash payment.')
      }
      return saveSupplierCashPayment({
        supplierKey: supplier.supplierKey,
        supplierTin: supplier.supplierTin,
        supplierName: supplier.supplierName,
        date: cashForm.date,
        amount: Number(cashForm.amount),
        note: cashForm.note,
      })
    },
    onSuccess: async () => {
      setCashForm((current) => ({ ...current, amount: '', note: '' }))
      await queryClient.invalidateQueries({ queryKey: ['supplier-debts'] })
      await queryClient.invalidateQueries({ queryKey: ['supplier-debt-transactions'] })
    },
  })

  const deleteCashMutation = useMutation({
    mutationFn: deleteSupplierCashPayment,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['supplier-debts'] })
      await queryClient.invalidateQueries({ queryKey: ['supplier-debt-transactions'] })
    },
  })

  const overview = debtQuery.data
  const suppliers = overview?.suppliers ?? []
  const selectedCashSupplier = suppliers.find((supplier) => supplier.supplierKey === cashForm.supplierKey)
  const cashAmount = Number(cashForm.amount)
  const canSaveCash = Boolean(selectedCashSupplier && cashForm.date && Number.isFinite(cashAmount) && cashAmount > 0)
  const loadingSources = debtQuery.isFetching || sourceRefreshMutation.isPending
  const loadError = sourceRefreshMutation.error instanceof Error ? sourceRefreshMutation.error : debtQuery.error

  useEffect(() => {
    if (!overview?.refreshInProgress) {
      return
    }
    const timeout = window.setTimeout(() => {
      void queryClient.invalidateQueries({
        queryKey: ['supplier-debts', dateFrom || 'default-opening-date', dateTo || 'today'],
      })
    }, 3000)
    return () => window.clearTimeout(timeout)
  }, [dateFrom, dateTo, overview?.refreshInProgress, queryClient])

  return (
    <div className="mx-auto max-w-[1500px] space-y-6">
      <section className="overflow-hidden rounded-[2rem] border border-slate-200 bg-slate-950 text-white shadow-xl">
        <div className="grid gap-6 p-6 lg:grid-cols-[1fr_auto] lg:p-8">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.32em] text-sky-300">Supplier ledger</p>
            <h1 className="mt-3 text-3xl font-black tracking-tight sm:text-4xl">{env.supplierDebtsTitle}</h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-300">{env.supplierDebtsInfo}</p>
          </div>
          <div className="grid min-w-[280px] gap-3 rounded-3xl border border-white/10 bg-white/10 p-4 backdrop-blur">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
              <label className="text-xs font-semibold uppercase tracking-wide text-slate-300">
                Date From
                <input
                  type="date"
                  value={dateFrom}
                  onChange={(event) => setDateFrom(event.target.value)}
                  className="mt-1 h-10 w-full rounded-xl border border-white/10 bg-white px-3 text-sm font-semibold text-slate-950 outline-none focus:ring-2 focus:ring-sky-300"
                />
              </label>
              <label className="text-xs font-semibold uppercase tracking-wide text-slate-300">
                Date To
                <input
                  type="date"
                  value={dateTo}
                  onChange={(event) => setDateTo(event.target.value)}
                  className="mt-1 h-10 w-full rounded-xl border border-white/10 bg-white px-3 text-sm font-semibold text-slate-950 outline-none focus:ring-2 focus:ring-sky-300"
                />
              </label>
            </div>
            <button
              type="button"
              onClick={() => sourceRefreshMutation.mutate()}
              disabled={loadingSources}
              className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-sky-300 px-4 text-sm font-black text-slate-950 transition hover:bg-sky-200 disabled:cursor-wait disabled:opacity-70"
            >
              <RefreshCcw className={`h-4 w-4 ${loadingSources ? 'animate-spin' : ''}`} />
              Refresh Bank/RS.ge Sources
            </button>
          </div>
        </div>
      </section>

      {loadError instanceof Error ? (
        <div className="flex items-center gap-2 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          <AlertCircle className="h-5 w-5 flex-shrink-0" />
          {loadError.message}
        </div>
      ) : null}

      {overview ? (
        <>
          <SnapshotStatus overview={overview} />
          <SourceStatusRail overview={overview} />
          <SummaryGrid overview={overview} />
          <AuditPanel
            audit={overview.latestAudit ?? null}
            isRunning={auditMutation.isPending}
            error={auditMutation.error instanceof Error ? auditMutation.error.message : null}
            onRun={() => auditMutation.mutate()}
          />

          <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
            <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="text-xl font-black text-slate-950">Creditor Balances</h2>
                  <p className="text-sm text-slate-500">
                    {overview.supplierCount} suppliers from {overview.dateFrom} to {overview.dateTo}
                  </p>
                </div>
                {loadingSources ? <span className="text-sm font-semibold text-slate-500">Refreshing...</span> : null}
              </div>

              <div className="mt-5 overflow-x-auto rounded-2xl border border-slate-100">
                <table className="min-w-[1080px] w-full text-sm">
                  <thead className="bg-slate-50 text-left text-xs font-bold uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-4 py-3">Supplier</th>
                      <th className="px-4 py-3">TIN</th>
                      <th className="px-4 py-3 text-right">Purchases</th>
                      <th className="px-4 py-3 text-right">BOG</th>
                      <th className="px-4 py-3 text-right">TBC</th>
                      <th className="px-4 py-3 text-right">Cash</th>
                      <th className="px-4 py-3 text-right">Debt Left</th>
                      <th className="px-4 py-3 text-right">Rows</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {suppliers.map((supplier) => {
                      const open = expandedSupplier === supplier.supplierKey
                      return (
                        <React.Fragment key={supplier.supplierKey}>
                          <tr
                            className="cursor-pointer transition hover:bg-sky-50/60"
                            onClick={() => setExpandedSupplier(open ? null : supplier.supplierKey)}
                          >
                            <td className="px-4 py-3 font-bold text-slate-900">
                              <span className="inline-flex items-center gap-2">
                                {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                                {supplier.supplierName}
                              </span>
                            </td>
                            <td className="px-4 py-3 font-mono text-xs text-slate-500">{supplier.supplierTin || '-'}</td>
                            <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatGel(supplier.purchaseTotal)}</td>
                            <td className="px-4 py-3 text-right font-semibold tabular-nums text-emerald-700">{formatGel(supplier.bogPaidTotal)}</td>
                            <td className="px-4 py-3 text-right font-semibold tabular-nums text-cyan-700">{formatGel(supplier.tbcPaidTotal)}</td>
                            <td className="px-4 py-3 text-right font-semibold tabular-nums text-amber-700">{formatGel(supplier.cashPaidTotal)}</td>
                            <td className={`px-4 py-3 text-right font-black tabular-nums ${supplier.debtLeft > 0 ? 'text-red-700' : 'text-emerald-700'}`}>
                              {formatGel(supplier.debtLeft)}
                            </td>
                            <td className="px-4 py-3 text-right text-slate-500">{supplier.purchaseCount} / {supplier.paymentCount}</td>
                          </tr>
                          {open ? (
                            <SupplierDebtDetails
                              supplier={supplierDetailsQuery.data ?? supplier}
                              isLoading={supplierDetailsQuery.isFetching}
                              error={supplierDetailsQuery.error instanceof Error ? supplierDetailsQuery.error.message : null}
                              deletingCashId={deleteCashMutation.variables ?? null}
                              onDeleteCash={(id) => deleteCashMutation.mutate(id)}
                            />
                          ) : null}
                        </React.Fragment>
                      )
                    })}
                    {suppliers.length === 0 ? (
                      <tr>
                        <td className="px-4 py-5 text-slate-500" colSpan={8}>No supplier purchase debt found for this range.</td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </section>

            <CashPaymentPanel
              suppliers={suppliers}
              form={cashForm}
              setForm={setCashForm}
              canSave={canSaveCash}
              isSaving={saveCashMutation.isPending}
              error={saveCashMutation.error instanceof Error ? saveCashMutation.error.message : null}
              onSave={() => saveCashMutation.mutate()}
            />
          </div>

          <UnmatchedPaymentsPanel
            groups={overview.unmatchedPaymentGroups ?? []}
            suppliers={suppliers}
            mappingDrafts={mappingDrafts}
            setMappingDrafts={setMappingDrafts}
            onSaveMapping={(group, supplier) => saveMappingMutation.mutate({ group, supplier })}
            savingMapping={saveMappingMutation.isPending}
            error={saveMappingMutation.error instanceof Error ? saveMappingMutation.error.message : null}
          />
        </>
      ) : null}
    </div>
  )
}

function SnapshotStatus({ overview }: { overview: SupplierDebtOverview }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm shadow-sm">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="font-black text-slate-950">Saved creditor snapshot</p>
          <p className="mt-1 text-slate-500">
            Showing the latest saved balances while the app refreshes RS.ge, BOG, and TBC in the background.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs font-bold text-slate-600">
          <span className="rounded-full bg-slate-100 px-3 py-1">
            Generated: {formatDateTime(overview.snapshotGeneratedAt)}
          </span>
          <span className={`rounded-full px-3 py-1 ${overview.refreshInProgress ? 'bg-sky-100 text-sky-800' : 'bg-emerald-100 text-emerald-800'}`}>
            {overview.refreshInProgress ? 'Background refresh running' : 'Snapshot ready'}
          </span>
          {overview.lastRefreshCompletedAt ? (
            <span className="rounded-full bg-slate-100 px-3 py-1">
              Last refresh: {formatDateTime(overview.lastRefreshCompletedAt)}
            </span>
          ) : null}
        </div>
      </div>
      {overview.lastRefreshError ? (
        <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">
          Last refresh error: {overview.lastRefreshError}
        </p>
      ) : null}
    </div>
  )
}

function AuditPanel({
  audit,
  isRunning,
  error,
  onRun,
}: {
  audit: SupplierDebtAudit | null
  isRunning: boolean
  error: string | null
  onRun: () => void
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <ClipboardCheck className="h-5 w-5 text-slate-500" />
            <h2 className="text-lg font-black text-slate-950">Random Correctness Check</h2>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Samples suppliers, recalculates them from fresh source calls, and compares saved debt totals.
          </p>
        </div>
        <button
          type="button"
          onClick={onRun}
          disabled={isRunning}
          className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 text-sm font-black text-white transition hover:bg-slate-800 disabled:cursor-wait disabled:opacity-60"
        >
          <RefreshCcw className={`h-4 w-4 ${isRunning ? 'animate-spin' : ''}`} />
          {isRunning ? 'Checking...' : 'Run Random Audit'}
        </button>
      </div>

      {error ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">{error}</p> : null}

      {audit ? (
        <div className="mt-4 rounded-2xl border border-slate-100 bg-slate-50 p-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`rounded-full px-3 py-1 text-xs font-black ${audit.passed ? 'bg-emerald-200 text-emerald-950' : 'bg-red-200 text-red-950'}`}>
              {audit.passed ? 'PASSED' : 'FAILED'}
            </span>
            <span className="text-xs font-bold text-slate-500">
              {audit.sampledSupplierCount} sampled, {audit.failedSupplierCount} failed, audited {formatDateTime(audit.auditedAt)}
            </span>
          </div>
          {audit.suppliers.length > 0 ? (
            <div className="mt-3 overflow-x-auto">
              <table className="min-w-[760px] w-full text-xs">
                <thead className="text-left font-bold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="py-2 pr-3">Supplier</th>
                    <th className="py-2 pr-3 text-right">Snapshot Debt</th>
                    <th className="py-2 pr-3 text-right">Fresh Debt</th>
                    <th className="py-2 pr-3 text-right">Difference</th>
                    <th className="py-2 pr-3">Result</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200">
                  {audit.suppliers.map((supplier) => (
                    <tr key={supplier.supplierKey}>
                      <td className="py-2 pr-3 font-semibold text-slate-700">{supplier.supplierName}</td>
                      <td className="py-2 pr-3 text-right font-bold tabular-nums">{formatGel(supplier.snapshotDebtLeft)}</td>
                      <td className="py-2 pr-3 text-right font-bold tabular-nums">{formatGel(supplier.freshDebtLeft)}</td>
                      <td className="py-2 pr-3 text-right font-bold tabular-nums">{formatGel(supplier.debtDifference)}</td>
                      <td className={supplier.passed ? 'py-2 pr-3 font-black text-emerald-700' : 'py-2 pr-3 font-black text-red-700'}>
                        {supplier.passed ? 'OK' : 'Mismatch'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}

function SourceStatusRail({ overview }: { overview: SupplierDebtOverview }) {
  return (
    <div className="grid gap-3 md:grid-cols-4">
      {overview.sourceStatuses.map((status) => {
        const ok = status.status === 'OK'
        return (
          <div
            key={status.source}
            className={`rounded-2xl border p-4 shadow-sm ${
              ok ? 'border-emerald-200 bg-emerald-50 text-emerald-950' : 'border-red-200 bg-red-50 text-red-950'
            }`}
          >
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-black">{status.source}</p>
              <span className={`rounded-full px-2 py-1 text-[11px] font-black ${ok ? 'bg-emerald-200 text-emerald-900' : 'bg-red-200 text-red-900'}`}>
                {status.status}
              </span>
            </div>
            <p className="mt-2 text-xl font-black tabular-nums">{formatGel(status.total)}</p>
            <p className="mt-1 text-xs font-semibold opacity-75">{status.recordCount} records</p>
            {status.message ? <p className="mt-2 line-clamp-2 text-xs font-semibold">{status.message}</p> : null}
          </div>
        )
      })}
    </div>
  )
}

function SummaryGrid({ overview }: { overview: SupplierDebtOverview }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
      <MetricCard label="Purchases" value={overview.purchaseTotal} icon={<Landmark className="h-5 w-5" />} />
      <MetricCard label="BOG Paid" value={overview.bogPaidTotal} tone="good" />
      <MetricCard label="TBC Paid" value={overview.tbcPaidTotal} tone="good" />
      <MetricCard label="Cash Paid" value={overview.cashPaidTotal} tone="warn" />
      <MetricCard label="Debt Left" value={overview.debtTotal} tone={overview.debtTotal > 0 ? 'bad' : 'good'} />
      <MetricCard label="Unmatched Bank" value={overview.unmatchedPaymentTotal} tone={overview.unmatchedPaymentTotal > 0 ? 'warn' : 'good'} />
    </div>
  )
}

function MetricCard({
  label,
  value,
  tone,
  icon,
}: {
  label: string
  value: number
  tone?: 'good' | 'bad' | 'warn'
  icon?: React.ReactNode
}) {
  const colors =
    tone === 'good'
      ? 'text-emerald-700'
      : tone === 'bad'
        ? 'text-red-700'
        : tone === 'warn'
          ? 'text-amber-700'
          : 'text-slate-950'
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p>
        {icon ? <span className="text-slate-400">{icon}</span> : null}
      </div>
      <p className={`mt-3 text-2xl font-black tabular-nums ${colors}`}>{formatGel(value)}</p>
    </div>
  )
}

function CashPaymentPanel({
  suppliers,
  form,
  setForm,
  canSave,
  isSaving,
  error,
  onSave,
}: {
  suppliers: SupplierDebtRow[]
  form: { supplierKey: string; date: string; amount: string; note: string }
  setForm: React.Dispatch<React.SetStateAction<{ supplierKey: string; date: string; amount: string; note: string }>>
  canSave: boolean
  isSaving: boolean
  error: string | null
  onSave: () => void
}) {
  return (
    <aside className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-2">
        <Wallet className="h-5 w-5 text-amber-600" />
        <h2 className="text-lg font-black text-slate-950">Add Cash Payment</h2>
      </div>
      <p className="mt-2 text-sm text-slate-500">Record manual supplier payments that did not pass through BOG or TBC.</p>

      <div className="mt-5 space-y-4">
        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Supplier
          <select
            className="mt-1 h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 outline-none focus:ring-2 focus:ring-sky-300"
            value={form.supplierKey}
            onChange={(event) => setForm((current) => ({ ...current, supplierKey: event.target.value }))}
          >
            <option value="">Select supplier</option>
            {suppliers.map((supplier) => (
              <option key={supplier.supplierKey} value={supplier.supplierKey}>
                {supplier.supplierName}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Payment Date
          <input
            type="date"
            value={form.date}
            onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
            className="mt-1 h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-semibold outline-none focus:ring-2 focus:ring-sky-300"
          />
        </label>

        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Amount
          <input
            type="number"
            min="0"
            step="0.01"
            value={form.amount}
            onChange={(event) => setForm((current) => ({ ...current, amount: event.target.value }))}
            className="mt-1 h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-semibold outline-none focus:ring-2 focus:ring-sky-300"
            placeholder="0.00"
          />
        </label>

        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Note
          <textarea
            value={form.note}
            onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))}
            className="mt-1 min-h-24 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium outline-none focus:ring-2 focus:ring-sky-300"
            placeholder="Optional receipt number, cashier note, or context"
          />
        </label>
      </div>

      {error ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">{error}</p> : null}

      <button
        type="button"
        disabled={!canSave || isSaving}
        onClick={onSave}
        className="mt-5 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 text-sm font-black text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Plus className="h-4 w-4" />
        {isSaving ? 'Saving...' : 'Save Cash Payment'}
      </button>
    </aside>
  )
}

function SupplierDebtDetails({
  supplier,
  isLoading,
  error,
  deletingCashId,
  onDeleteCash,
}: {
  supplier: SupplierDebtRow
  isLoading: boolean
  error: string | null
  deletingCashId: string | null
  onDeleteCash: (id: string) => void
}) {
  return (
    <tr>
      <td colSpan={8} className="bg-slate-50 px-4 py-4">
        {isLoading ? <p className="mb-3 text-sm font-semibold text-slate-500">Loading supplier transactions...</p> : null}
        {error ? <p className="mb-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">{error}</p> : null}
        <div className="grid gap-4 lg:grid-cols-2">
          <DetailList
            title="rs.ge Purchases"
            rows={supplier.purchases.map((purchase) => ({
              key: purchase.waybillNumber,
              date: purchase.date,
              provider: 'RSGE',
              text: purchase.waybillNumber,
              amount: purchase.amount,
            }))}
          />
          <DetailList
            title="Payments"
            rows={supplier.payments.map((payment) => ({
              key: payment.id || payment.reference || `${payment.date}-${payment.amount}`,
              date: payment.date,
              provider: payment.provider,
              text: payment.counterparty || payment.description || payment.reference,
              amount: payment.amount,
              removable: payment.provider === 'CASH',
            }))}
            deletingId={deletingCashId}
            onDelete={onDeleteCash}
          />
        </div>
      </td>
    </tr>
  )
}

function DetailList({
  title,
  rows,
  deletingId,
  onDelete,
}: {
  title: string
  rows: { key: string; date: string | null; provider: string; text: string; amount: number; removable?: boolean }[]
  deletingId?: string | null
  onDelete?: (id: string) => void
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-3 py-2 text-sm font-bold text-slate-700">{title}</div>
      <div className="max-h-72 overflow-auto divide-y divide-slate-100">
        {rows.map((row, index) => (
          <div key={`${row.key}-${index}`} className="grid grid-cols-[90px_64px_1fr_120px_auto] items-center gap-2 px-3 py-2 text-xs">
            <span className="text-slate-500">{row.date || '-'}</span>
            <span className="rounded-full bg-slate-100 px-2 py-1 text-center font-black text-slate-600">{row.provider}</span>
            <span className="truncate text-slate-700">{row.text || '-'}</span>
            <span className="text-right font-bold tabular-nums">{formatGel(row.amount)}</span>
            {row.removable && onDelete ? (
              <button
                type="button"
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-red-600 hover:bg-red-50 disabled:opacity-50"
                disabled={deletingId === row.key}
                onClick={() => onDelete(row.key)}
                aria-label="Delete cash payment"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            ) : (
              <span className="h-8 w-8" />
            )}
          </div>
        ))}
        {rows.length === 0 ? <p className="px-3 py-4 text-sm text-slate-500">No rows.</p> : null}
      </div>
    </div>
  )
}

function UnmatchedPaymentsPanel({
  groups,
  suppliers,
  mappingDrafts,
  setMappingDrafts,
  onSaveMapping,
  savingMapping,
  error,
}: {
  groups: SupplierDebtUnmatchedGroup[]
  suppliers: SupplierDebtRow[]
  mappingDrafts: Record<string, string>
  setMappingDrafts: React.Dispatch<React.SetStateAction<Record<string, string>>>
  onSaveMapping: (group: SupplierDebtUnmatchedGroup, supplier: SupplierDebtRow) => void
  savingMapping: boolean
  error: string | null
}) {
  return (
    <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5 shadow-sm">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-lg font-black text-amber-950">Unmatched Bank Debit Groups</h2>
          <p className="mt-1 text-sm text-amber-800">
            Groups reuse provider + TIN/account/name/description. One mapping fixes every repeated transaction in that group.
          </p>
        </div>
        <span className="rounded-full bg-amber-200 px-3 py-1 text-xs font-black text-amber-950">{groups.length} open groups</span>
      </div>

      {error ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">{error}</p> : null}

      <div className="mt-4 space-y-3">
        {groups.map((group) => {
          const key = group.groupKey
          const selectedSupplier = suppliers.find((supplier) => supplier.supplierKey === mappingDrafts[key])
          return (
            <div key={key} className="rounded-2xl border border-amber-200 bg-white p-4">
              <div className="grid gap-3 lg:grid-cols-[1fr_260px_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-slate-950 px-2 py-1 text-[11px] font-black text-white">{group.provider}</span>
                    <span className="rounded-full bg-amber-100 px-2 py-1 text-[11px] font-black text-amber-900">{group.matchType}</span>
                    <p className="truncate text-sm font-black text-slate-900">{group.counterparty || group.matchText || 'Unknown counterparty'}</p>
                  </div>
                  <p className="mt-2 text-xs font-semibold text-slate-500">
                    {group.transactionCount} transactions - {formatGel(group.amount)} total - largest {formatGel(group.largestTransaction)}
                  </p>
                  <p className="mt-1 truncate text-xs text-slate-500">
                    Mapping value: {group.matchText || 'no identifier'}{group.description ? ` - ${group.description}` : ''}
                  </p>
                  {group.examples.length > 0 ? (
                    <div className="mt-2 grid gap-1 text-[11px] font-medium text-slate-500">
                      {group.examples.map((example, index) => (
                        <span key={`${group.groupKey}-${example.reference}-${index}`} className="truncate">
                          {example.date || '-'} - {formatGel(example.amount)} - {example.description || example.reference || example.counterparty}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
                <select
                  className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold outline-none focus:ring-2 focus:ring-sky-300"
                  value={mappingDrafts[key] ?? ''}
                  onChange={(event) => setMappingDrafts((current) => ({ ...current, [key]: event.target.value }))}
                >
                  <option value="">Select supplier</option>
                  {suppliers.map((supplier) => (
                    <option key={supplier.supplierKey} value={supplier.supplierKey}>
                      {supplier.supplierName}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={!selectedSupplier || savingMapping}
                  onClick={() => selectedSupplier && onSaveMapping(group, selectedSupplier)}
                >
                  <Save className="h-4 w-4" />
                  Save Group Mapping
                </button>
              </div>
            </div>
          )
        })}
        {groups.length === 0 ? <p className="text-sm font-semibold text-amber-800">All bank debit payments are matched.</p> : null}
      </div>
    </section>
  )
}
