import React, { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  Activity,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  Landmark,
  Plus,
  RefreshCcw,
  Save,
  Search,
  ShieldCheck,
  Trash2,
  Wallet,
  X,
} from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
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
const DEFAULT_DATE_FROM = '2025-01-01'

const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return dateTimeFormatter.format(new Date(value))
}

function matchesSupplierFilter(supplier: SupplierDebtRow, query: string) {
  const normalizedQuery = query.trim().toLowerCase()
  if (!normalizedQuery) {
    return true
  }
  return [supplier.supplierName, supplier.supplierTin, supplier.supplierKey]
    .filter(Boolean)
    .some((value) => value.toLowerCase().includes(normalizedQuery))
}

type SupplierDebtDetailsContentProps = {
  supplier: SupplierDebtRow
  isLoading: boolean
  error: string | null
  deletingCashId: string | null
  pendingCashDeleteId: string | null
  onRequestCashDelete: (id: string) => void
  onCancelCashDelete: () => void
  onConfirmCashDelete: (id: string) => void
}

export default function SupplierDebtsPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [dateFrom, setDateFrom] = useState(() => searchParams.get('from') ?? DEFAULT_DATE_FROM)
  const [dateTo, setDateTo] = useState(() => searchParams.get('to') ?? today())
  const [expandedSupplier, setExpandedSupplier] = useState<string | null>(() => searchParams.get('supplier'))
  const [supplierFilter, setSupplierFilter] = useState(() => searchParams.get('q') ?? '')
  const [mappingDrafts, setMappingDrafts] = useState<Record<string, string>>({})
  const [pendingCashDeleteId, setPendingCashDeleteId] = useState<string | null>(null)
  const [manualPaymentSupplierKey, setManualPaymentSupplierKey] = useState<string | null>(null)
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

  const manualDetailsQuery = useQuery({
    queryKey: ['supplier-debt-transactions', manualPaymentSupplierKey, dateFrom || 'default-opening-date', dateTo || 'today', 'manual-payments'],
    queryFn: () => getSupplierDebtSupplierTransactions(manualPaymentSupplierKey ?? '', dateFrom || undefined, dateTo || undefined),
    enabled: Boolean(manualPaymentSupplierKey),
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
  const filteredSuppliers = suppliers.filter((supplier) => matchesSupplierFilter(supplier, supplierFilter))
  const topDebtSupplier = suppliers.find((supplier) => supplier.debtLeft > 0)
  const visibleDebtTotal = filteredSuppliers.reduce((total, supplier) => total + supplier.debtLeft, 0)
  const manualPaymentSupplier = suppliers.find((supplier) => supplier.supplierKey === manualPaymentSupplierKey) ?? null

  function syncSearchParams(patch: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams)
    Object.entries(patch).forEach(([key, value]) => {
      if (!value) {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    })
    setSearchParams(next, { replace: true })
  }

  function updateDateFrom(value: string) {
    setDateFrom(value)
    syncSearchParams({ from: value || null })
  }

  function updateDateTo(value: string) {
    setDateTo(value)
    syncSearchParams({ to: value || null })
  }

  function updateSupplierFilter(value: string) {
    setSupplierFilter(value)
    syncSearchParams({ q: value || null })
  }

  function toggleSupplier(supplierKey: string) {
    const nextSupplier = expandedSupplier === supplierKey ? null : supplierKey
    setExpandedSupplier(nextSupplier)
    syncSearchParams({ supplier: nextSupplier })
  }

  function openManualPayments(supplier: SupplierDebtRow) {
    setPendingCashDeleteId(null)
    setManualPaymentSupplierKey(supplier.supplierKey)
    setCashForm({
      supplierKey: supplier.supplierKey,
      date: today(),
      amount: '',
      note: '',
    })
  }

  function closeManualPayments() {
    setManualPaymentSupplierKey(null)
    setPendingCashDeleteId(null)
    setCashForm((current) => ({ ...current, amount: '', note: '' }))
  }

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
    <main className="mx-auto max-w-[1500px] space-y-3 overflow-x-hidden text-xs sm:space-y-4 sm:text-[13px]">
      <section className="relative overflow-hidden rounded-3xl border border-slate-900 bg-[#101820] text-white shadow-2xl shadow-slate-300/50 sm:rounded-[2rem]">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_12%_20%,rgba(56,189,248,0.26),transparent_30%),radial-gradient(circle_at_82%_10%,rgba(245,158,11,0.22),transparent_28%),linear-gradient(135deg,rgba(255,255,255,0.10)_0,transparent_42%)]" />
        <div className="absolute -bottom-24 left-8 h-48 w-48 rounded-full border border-white/10" />
        <div className="relative grid gap-4 p-4 sm:p-5 lg:grid-cols-[1fr_330px] lg:p-5">
          <div className="min-w-0">
            <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-cyan-200 sm:text-xs sm:tracking-[0.34em]">Supplier Ledger Control Room</p>
            <h1 className="mt-2 max-w-4xl text-balance text-2xl font-black tracking-tight sm:text-3xl">{env.supplierDebtsTitle}</h1>
            <p className="mt-2 max-w-3xl text-pretty text-xs leading-5 text-slate-300 sm:text-[13px]">{env.supplierDebtsInfo}</p>
            {overview ? (
              <div className="mt-5 grid gap-2 sm:grid-cols-3">
                <HeroStat label="Outstanding" value={formatGel(overview.debtTotal)} tone={overview.debtTotal > 0 ? 'bad' : 'good'} />
                <HeroStat label="Top Creditor" value={topDebtSupplier?.supplierName ?? 'No Open Debt'} />
                <HeroStat
                  label="Unmatched"
                  value={`${overview.unmatchedPaymentGroups?.length ?? 0} Groups`}
                  tone={(overview.unmatchedPaymentGroups?.length ?? 0) > 0 ? 'warn' : 'good'}
                />
              </div>
            ) : null}
          </div>
          <div className="grid gap-3 rounded-2xl border border-white/15 bg-white/10 p-3 shadow-2xl shadow-black/20 backdrop-blur sm:rounded-3xl sm:p-4">
            <div className="flex items-center gap-2 text-[13px] font-black text-white sm:text-sm">
              <Activity className="h-4 w-4 text-cyan-200" aria-hidden="true" />
              Source Refresh Window
            </div>
            <div className="grid gap-3">
              <label className="text-xs font-semibold uppercase tracking-wide text-slate-300">
                Date From
                <input
                  type="date"
                  name="supplier-debt-date-from"
                  autoComplete="off"
                  value={dateFrom}
                  onChange={(event) => updateDateFrom(event.target.value)}
                  className="mt-1 h-11 w-full rounded-xl border border-white/10 bg-white px-3 text-sm font-semibold text-slate-950 transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-200"
                />
              </label>
              <label className="text-xs font-semibold uppercase tracking-wide text-slate-300">
                Date To
                <input
                  type="date"
                  name="supplier-debt-date-to"
                  autoComplete="off"
                  value={dateTo}
                  onChange={(event) => updateDateTo(event.target.value)}
                  className="mt-1 h-11 w-full rounded-xl border border-white/10 bg-white px-3 text-sm font-semibold text-slate-950 transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-200"
                />
              </label>
            </div>
            <button
              type="button"
              onClick={() => sourceRefreshMutation.mutate()}
              disabled={loadingSources}
              className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-cyan-200 px-4 py-2 text-[13px] font-black text-slate-950 transition-colors hover:bg-white focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-200/70 disabled:cursor-wait disabled:opacity-70 sm:text-sm"
            >
              <RefreshCcw className={`h-4 w-4 ${loadingSources ? 'animate-spin' : ''}`} aria-hidden="true" />
              Refresh Bank/RS.ge Sources
            </button>
          </div>
        </div>
      </section>

      {loadError instanceof Error ? (
        <div className="flex items-start gap-2 rounded-2xl border border-red-200 bg-red-50 p-3 text-[13px] font-semibold text-red-700 sm:p-4 sm:text-sm" aria-live="polite">
          <AlertCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
          {loadError.message}. Check source credentials, then refresh again.
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

          <div>
            <section className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <h2 className="text-base font-black text-slate-950 sm:text-lg">Creditor Balances</h2>
                  <p className="text-xs text-slate-500 sm:text-[13px]">
                    {filteredSuppliers.length} of {overview.supplierCount} suppliers shown · visible debt {formatGel(visibleDebtTotal)}
                  </p>
                </div>
                <label className="relative min-w-0 sm:w-72">
                  <span className="sr-only">Search Suppliers</span>
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                  <input
                    type="search"
                    name="supplier-debt-search"
                    autoComplete="off"
                    value={supplierFilter}
                    onChange={(event) => updateSupplierFilter(event.target.value)}
                    placeholder="Search supplier or TIN…"
                    className="h-11 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-10 pr-3 text-[13px] font-semibold text-slate-900 transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 sm:text-sm"
                  />
                </label>
              </div>

              <div className="mt-4 grid gap-3 md:hidden">
                {filteredSuppliers.map((supplier) => {
                  const open = expandedSupplier === supplier.supplierKey
                  return (
                    <SupplierMobileCard
                      key={supplier.supplierKey}
                      supplier={supplier}
                      detailsSupplier={supplierDetailsQuery.data ?? supplier}
                      open={open}
                      isLoading={supplierDetailsQuery.isFetching}
                      error={supplierDetailsQuery.error instanceof Error ? supplierDetailsQuery.error.message : null}
                      deletingCashId={deleteCashMutation.variables ?? null}
                      pendingCashDeleteId={pendingCashDeleteId}
                      onToggle={() => toggleSupplier(supplier.supplierKey)}
                      onOpenManualPayments={() => openManualPayments(supplier)}
                      onRequestCashDelete={setPendingCashDeleteId}
                      onCancelCashDelete={() => setPendingCashDeleteId(null)}
                      onConfirmCashDelete={(id) => {
                        setPendingCashDeleteId(null)
                        deleteCashMutation.mutate(id)
                      }}
                    />
                  )
                })}
                {filteredSuppliers.length === 0 ? (
                  <p className="rounded-2xl border border-slate-100 bg-slate-50 p-4 text-slate-500">No suppliers match this search and date range.</p>
                ) : null}
              </div>

              <div className="mt-4 hidden overflow-x-auto rounded-2xl border border-slate-100 md:block">
                <table className="min-w-[1120px] w-full text-xs">
                  <thead className="bg-slate-50 text-left text-[11px] font-bold uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-3 py-2.5">Supplier</th>
                      <th className="px-3 py-2.5">TIN</th>
                      <th className="px-3 py-2.5 text-right">Purchases</th>
                      <th className="px-3 py-2.5 text-right">BOG</th>
                      <th className="px-3 py-2.5 text-right">TBC</th>
                      <th className="px-3 py-2.5 text-right">Manual</th>
                      <th className="px-3 py-2.5 text-right">Debt Left</th>
                      <th className="px-3 py-2.5 text-right">Rows</th>
                      <th className="px-3 py-2.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {filteredSuppliers.map((supplier) => {
                      const open = expandedSupplier === supplier.supplierKey
                      return (
                        <React.Fragment key={supplier.supplierKey}>
                          <tr className="transition-colors hover:bg-cyan-50/70">
                            <td className="px-3 py-2.5 font-bold text-slate-900">
                              <button
                                type="button"
                                aria-expanded={open}
                                onClick={() => toggleSupplier(supplier.supplierKey)}
                                className="inline-flex max-w-full items-center gap-2 rounded-xl text-left transition-colors hover:text-cyan-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
                              >
                                {open ? <ChevronDown className="h-4 w-4" aria-hidden="true" /> : <ChevronRight className="h-4 w-4" aria-hidden="true" />}
                                <span className="max-w-[360px] truncate">{supplier.supplierName}</span>
                                {supplier.newFromRsge ? <NewRsgeBadge /> : null}
                              </button>
                            </td>
                            <td className="px-3 py-2.5 font-mono text-[11px] text-slate-500">{supplier.supplierTin || '-'}</td>
                            <td className="px-3 py-2.5 text-right font-semibold tabular-nums">{formatGel(supplier.purchaseTotal)}</td>
                            <td className="px-3 py-2.5 text-right font-semibold tabular-nums text-emerald-700">{formatGel(supplier.bogPaidTotal)}</td>
                            <td className="px-3 py-2.5 text-right font-semibold tabular-nums text-cyan-700">{formatGel(supplier.tbcPaidTotal)}</td>
                            <td className="px-3 py-2.5 text-right font-semibold tabular-nums text-amber-700">{formatGel(supplier.cashPaidTotal)}</td>
                            <td className={`px-3 py-2.5 text-right font-black tabular-nums ${supplier.debtLeft > 0 ? 'text-red-700' : 'text-emerald-700'}`}>
                              {formatGel(supplier.debtLeft)}
                            </td>
                            <td className="px-3 py-2.5 text-right text-slate-500">{supplier.purchaseCount} / {supplier.paymentCount}</td>
                            <td className="px-3 py-2.5 text-right">
                              <button
                                type="button"
                                onClick={() => openManualPayments(supplier)}
                                className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg bg-amber-100 px-2.5 text-[11px] font-black text-amber-900 transition-colors hover:bg-amber-200 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100"
                              >
                                <Wallet className="h-3.5 w-3.5" aria-hidden="true" />
                                Manual
                              </button>
                            </td>
                          </tr>
                          {open ? (
                            <SupplierDebtDetails
                              supplier={supplierDetailsQuery.data ?? supplier}
                              isLoading={supplierDetailsQuery.isFetching}
                              error={supplierDetailsQuery.error instanceof Error ? supplierDetailsQuery.error.message : null}
                              deletingCashId={deleteCashMutation.variables ?? null}
                              pendingCashDeleteId={pendingCashDeleteId}
                              onRequestCashDelete={setPendingCashDeleteId}
                              onCancelCashDelete={() => setPendingCashDeleteId(null)}
                              onConfirmCashDelete={(id) => {
                                setPendingCashDeleteId(null)
                                deleteCashMutation.mutate(id)
                              }}
                            />
                          ) : null}
                        </React.Fragment>
                      )
                    })}
                    {filteredSuppliers.length === 0 ? (
                      <tr>
                        <td className="px-4 py-5 text-slate-500" colSpan={9}>No suppliers match this search and date range.</td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </section>
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

          {manualPaymentSupplier ? (
            <ManualPaymentsModal
              supplier={manualDetailsQuery.data ?? manualPaymentSupplier}
              fallbackSupplier={manualPaymentSupplier}
              form={cashForm}
              setForm={setCashForm}
              canSave={canSaveCash}
              isSaving={saveCashMutation.isPending}
              saveError={saveCashMutation.error instanceof Error ? saveCashMutation.error.message : null}
              isLoading={manualDetailsQuery.isFetching}
              loadError={manualDetailsQuery.error instanceof Error ? manualDetailsQuery.error.message : null}
              deletingCashId={deleteCashMutation.variables ?? null}
              pendingCashDeleteId={pendingCashDeleteId}
              onRequestCashDelete={setPendingCashDeleteId}
              onCancelCashDelete={() => setPendingCashDeleteId(null)}
              onConfirmCashDelete={(id) => {
                setPendingCashDeleteId(null)
                deleteCashMutation.mutate(id)
              }}
              onSave={() => saveCashMutation.mutate()}
              onClose={closeManualPayments}
            />
          ) : null}
        </>
      ) : null}
    </main>
  )
}

function HeroStat({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone?: 'good' | 'bad' | 'warn'
}) {
  const toneClass =
    tone === 'good'
      ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald-100'
      : tone === 'bad'
        ? 'border-red-300/30 bg-red-300/10 text-red-100'
        : tone === 'warn'
          ? 'border-amber-300/30 bg-amber-300/10 text-amber-100'
          : 'border-white/10 bg-white/10 text-white'
  return (
    <div className={`min-w-0 rounded-2xl border p-3 ${toneClass}`}>
      <p className="text-[11px] font-black uppercase tracking-[0.22em] opacity-70">{label}</p>
      <p className="mt-2 truncate text-base font-black tabular-nums">{value}</p>
    </div>
  )
}

function SupplierMobileCard({
  supplier,
  detailsSupplier,
  open,
  isLoading,
  error,
  deletingCashId,
  pendingCashDeleteId,
  onToggle,
  onRequestCashDelete,
  onCancelCashDelete,
  onConfirmCashDelete,
  onOpenManualPayments,
}: SupplierDebtDetailsContentProps & {
  detailsSupplier: SupplierDebtRow
  open: boolean
  onToggle: () => void
  onOpenManualPayments: () => void
}) {
  const debtTone = supplier.debtLeft > 0 ? 'text-red-700' : 'text-emerald-700'
  return (
    <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <button
        type="button"
        aria-expanded={open}
        onClick={onToggle}
        className="flex min-h-14 w-full items-start justify-between gap-3 px-3 py-3 text-left transition-colors hover:bg-cyan-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
      >
        <span className="flex min-w-0 items-start gap-2">
          {open ? <ChevronDown className="mt-0.5 h-4 w-4 flex-shrink-0 text-cyan-700" aria-hidden="true" /> : <ChevronRight className="mt-0.5 h-4 w-4 flex-shrink-0 text-slate-400" aria-hidden="true" />}
          <span className="min-w-0">
            <span className="flex flex-wrap items-center gap-1.5">
              <span className="line-clamp-2 text-xs font-black leading-5 text-slate-950">{supplier.supplierName}</span>
              {supplier.newFromRsge ? <NewRsgeBadge /> : null}
            </span>
            <span className="mt-1 block font-mono text-[11px] font-semibold text-slate-500">{supplier.supplierTin || 'No TIN'}</span>
          </span>
        </span>
        <span className={`shrink-0 text-right text-[13px] font-black tabular-nums ${debtTone}`}>
          {formatGel(supplier.debtLeft)}
        </span>
      </button>

      <div className="grid grid-cols-2 gap-2 border-t border-slate-100 bg-slate-50/70 p-3 text-[11px]">
        <MobileMetric label="Purchases" value={formatGel(supplier.purchaseTotal)} />
        <MobileMetric label="BOG" value={formatGel(supplier.bogPaidTotal)} tone="good" />
        <MobileMetric label="TBC" value={formatGel(supplier.tbcPaidTotal)} tone="info" />
        <MobileMetric label="Cash" value={formatGel(supplier.cashPaidTotal)} tone="warn" />
        <MobileMetric label="Rows" value={`${supplier.purchaseCount} / ${supplier.paymentCount}`} />
        <MobileMetric label="Left" value={formatGel(supplier.debtLeft)} tone={supplier.debtLeft > 0 ? 'bad' : 'good'} />
      </div>

      <div className="border-t border-slate-100 bg-white px-3 py-2">
        <button
          type="button"
          onClick={onOpenManualPayments}
          className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-amber-100 px-3 text-xs font-black text-amber-900 transition-colors hover:bg-amber-200 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100"
        >
          <Wallet className="h-4 w-4" aria-hidden="true" />
          Manual payments ({supplier.cashPaymentCount})
        </button>
      </div>

      {open ? (
        <div className="border-t border-slate-100 bg-slate-50 p-3">
          <SupplierDebtDetailsContent
            supplier={detailsSupplier}
            isLoading={isLoading}
            error={error}
            deletingCashId={deletingCashId}
            pendingCashDeleteId={pendingCashDeleteId}
            onRequestCashDelete={onRequestCashDelete}
            onCancelCashDelete={onCancelCashDelete}
            onConfirmCashDelete={onConfirmCashDelete}
          />
        </div>
      ) : null}
    </article>
  )
}

function MobileMetric({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone?: 'good' | 'bad' | 'warn' | 'info'
}) {
  const color =
    tone === 'good'
      ? 'text-emerald-700'
      : tone === 'bad'
        ? 'text-red-700'
        : tone === 'warn'
          ? 'text-amber-700'
          : tone === 'info'
            ? 'text-cyan-700'
            : 'text-slate-900'
  return (
    <div className="min-w-0 rounded-xl border border-slate-100 bg-white px-2 py-2">
      <p className="truncate font-black uppercase tracking-wide text-slate-400">{label}</p>
      <p className={`mt-1 truncate font-black tabular-nums ${color}`}>{value}</p>
    </div>
  )
}

function SnapshotStatus({ overview }: { overview: SupplierDebtOverview }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-3 text-[13px] shadow-sm sm:p-4 sm:text-sm" aria-live="polite">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0">
          <p className="flex items-center gap-2 font-black text-slate-950">
            <ShieldCheck className="h-4 w-4 text-emerald-600" aria-hidden="true" />
            Saved Creditor Snapshot
          </p>
          <p className="mt-1 text-slate-500">
            Showing the latest saved balances while the app refreshes RS.ge, BOG, and TBC in the background.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-[11px] font-bold text-slate-600 sm:text-xs">
          <span className="rounded-full bg-slate-100 px-2.5 py-1 sm:px-3">
            Generated: {formatDateTime(overview.snapshotGeneratedAt)}
          </span>
          <span className={`rounded-full px-3 py-1 ${overview.refreshInProgress ? 'bg-sky-100 text-sky-800' : 'bg-emerald-100 text-emerald-800'}`}>
            {overview.refreshInProgress ? 'Background Refresh Running' : 'Snapshot Ready'}
          </span>
          {overview.lastRefreshCompletedAt ? (
            <span className="rounded-full bg-slate-100 px-2.5 py-1 sm:px-3">
              Last refresh: {formatDateTime(overview.lastRefreshCompletedAt)}
            </span>
          ) : null}
        </div>
      </div>
      {overview.lastRefreshError ? (
        <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-[13px] font-semibold text-red-700 sm:text-sm">
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
    <section className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <ClipboardCheck className="h-5 w-5 text-slate-500" aria-hidden="true" />
            <h2 className="text-sm font-black text-slate-950 sm:text-base">Random Correctness Check</h2>
          </div>
          <p className="mt-1 text-xs text-slate-500 sm:text-[13px]">
            Samples suppliers, recalculates them from fresh source calls, and compares saved debt totals.
          </p>
        </div>
        <button
          type="button"
          onClick={onRun}
          disabled={isRunning}
          className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-2 text-[13px] font-black text-white transition hover:bg-slate-800 disabled:cursor-wait disabled:opacity-60 sm:text-sm"
        >
          <RefreshCcw className={`h-4 w-4 ${isRunning ? 'animate-spin' : ''}`} />
          {isRunning ? 'Checking…' : 'Run Random Audit'}
        </button>
      </div>

      {error ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-[13px] font-semibold text-red-700 sm:text-sm">{error}</p> : null}

      {audit ? (
        <div className="mt-4 rounded-2xl border border-slate-100 bg-slate-50 p-3 sm:p-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`rounded-full px-3 py-1 text-xs font-black ${audit.passed ? 'bg-emerald-200 text-emerald-950' : 'bg-red-200 text-red-950'}`}>
              {audit.passed ? 'PASSED' : 'FAILED'}
            </span>
            <span className="text-xs font-bold text-slate-500">
              {audit.sampledSupplierCount} sampled, {audit.failedSupplierCount} failed, audited {formatDateTime(audit.auditedAt)}
            </span>
          </div>
          {audit.suppliers.length > 0 ? (
            <>
            <div className="mt-3 grid gap-2 sm:hidden">
              {audit.suppliers.map((supplier) => (
                <div key={supplier.supplierKey} className="rounded-xl border border-slate-200 bg-white p-3 text-[11px]">
                  <div className="flex items-start justify-between gap-3">
                    <p className="line-clamp-2 font-black text-slate-800">{supplier.supplierName}</p>
                    <span className={supplier.passed ? 'shrink-0 font-black text-emerald-700' : 'shrink-0 font-black text-red-700'}>
                      {supplier.passed ? 'OK' : 'Mismatch'}
                    </span>
                  </div>
                  <div className="mt-2 grid grid-cols-3 gap-2">
                    <MobileMetric label="Snapshot" value={formatGel(supplier.snapshotDebtLeft)} />
                    <MobileMetric label="Fresh" value={formatGel(supplier.freshDebtLeft)} />
                    <MobileMetric label="Diff" value={formatGel(supplier.debtDifference)} tone={supplier.passed ? 'good' : 'bad'} />
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-3 hidden overflow-x-auto sm:block">
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
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}

function SourceStatusRail({ overview }: { overview: SupplierDebtOverview }) {
  return (
    <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
      {overview.sourceStatuses.map((status) => {
        const ok = status.status === 'OK'
        const warning = status.status === 'WARNING'
        const details = status.technicalDetails || status.message
        return (
          <div
            key={status.source}
            className={`rounded-2xl border p-3 shadow-sm sm:p-4 ${
              ok
                ? 'border-emerald-200 bg-emerald-50 text-emerald-950'
                : warning
                  ? 'border-amber-200 bg-amber-50 text-amber-950'
                  : 'border-red-200 bg-red-50 text-red-950'
            }`}
          >
            <div className="flex items-center justify-between gap-3">
              <p className="text-[13px] font-black sm:text-sm">{status.source}</p>
              <span className={`rounded-full px-2 py-1 text-[11px] font-black ${
                ok
                  ? 'bg-emerald-200 text-emerald-900'
                  : warning
                    ? 'bg-amber-200 text-amber-900'
                    : 'bg-red-200 text-red-900'
              }`}>
                {status.status}
              </span>
            </div>
            <p className="mt-2 text-base font-black tabular-nums sm:text-lg">{formatGel(status.total)}</p>
            <p className="mt-1 text-xs font-semibold opacity-75">{status.recordCount} records</p>
            {status.message ? <p className="mt-2 text-xs font-semibold">{status.message}</p> : null}
            {!ok && details ? (
              <details className="mt-3 rounded-xl border border-current/20 bg-white/50 p-3 text-xs">
                <summary className="cursor-pointer font-black uppercase tracking-wide">Show Full Trace</summary>
                <pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-slate-950 p-3 font-mono text-[11px] leading-5 text-slate-100">
                  {details}
                </pre>
              </details>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

function SummaryGrid({ overview }: { overview: SupplierDebtOverview }) {
  return (
    <div className="grid grid-cols-2 gap-2 lg:grid-cols-3 xl:grid-cols-6">
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
    <div className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p>
        {icon ? <span className="text-slate-400" aria-hidden="true">{icon}</span> : null}
      </div>
      <p className={`mt-2 text-base font-black tabular-nums sm:text-lg ${colors}`}>{formatGel(value)}</p>
    </div>
  )
}

function NewRsgeBadge() {
  return (
    <span className="shrink-0 rounded-full border border-cyan-200 bg-cyan-50 px-2 py-0.5 text-[10px] font-black uppercase tracking-wide text-cyan-800">
      New RS.ge
    </span>
  )
}

function ManualPaymentsModal({
  supplier,
  fallbackSupplier,
  form,
  setForm,
  canSave,
  isSaving,
  saveError,
  isLoading,
  loadError,
  deletingCashId,
  pendingCashDeleteId,
  onRequestCashDelete,
  onCancelCashDelete,
  onConfirmCashDelete,
  onSave,
  onClose,
}: {
  supplier: SupplierDebtRow
  fallbackSupplier: SupplierDebtRow
  form: { supplierKey: string; date: string; amount: string; note: string }
  setForm: React.Dispatch<React.SetStateAction<{ supplierKey: string; date: string; amount: string; note: string }>>
  canSave: boolean
  isSaving: boolean
  saveError: string | null
  isLoading: boolean
  loadError: string | null
  deletingCashId: string | null
  pendingCashDeleteId: string | null
  onRequestCashDelete: (id: string) => void
  onCancelCashDelete: () => void
  onConfirmCashDelete: (id: string) => void
  onSave: () => void
  onClose: () => void
}) {
  const manualPayments = supplier.payments.filter((payment) => payment.provider === 'CASH')
  const cashTotal = supplier.cashPaidTotal || fallbackSupplier.cashPaidTotal
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/55 p-3 backdrop-blur-sm sm:items-center" role="dialog" aria-modal="true">
      <div className="max-h-[92dvh] w-full max-w-3xl overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 bg-slate-950 px-4 py-3 text-white sm:px-5">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <Wallet className="h-4 w-4 text-amber-300" aria-hidden="true" />
              <p className="text-xs font-black uppercase tracking-[0.2em] text-amber-200">Manual payments</p>
            </div>
            <h2 className="mt-1 truncate text-lg font-black sm:text-xl">{supplier.supplierName || fallbackSupplier.supplierName}</h2>
            <p className="mt-1 font-mono text-[11px] font-semibold text-slate-300">{supplier.supplierTin || fallbackSupplier.supplierTin || 'No TIN'}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-slate-200 transition-colors hover:bg-white/10 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/20"
            aria-label="Close manual payments"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div className="grid max-h-[calc(92dvh-78px)] gap-4 overflow-y-auto p-4 sm:grid-cols-[1fr_290px] sm:p-5">
          <section className="min-w-0 rounded-2xl border border-slate-200 bg-slate-50">
            <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-3 py-2">
              <div>
                <p className="text-xs font-black uppercase tracking-wide text-slate-500">Saved manual ledger</p>
                <p className="text-lg font-black tabular-nums text-amber-700">{formatGel(cashTotal)}</p>
              </div>
              <span className="rounded-full bg-white px-2.5 py-1 text-[11px] font-black text-slate-600">
                {manualPayments.length} rows
              </span>
            </div>

            {isLoading ? <p className="p-3 text-xs font-semibold text-slate-500">Loading manual payments...</p> : null}
            {loadError ? <p className="m-3 rounded-xl border border-red-200 bg-red-50 p-3 text-xs font-semibold text-red-700">{loadError}</p> : null}

            <DetailList
              title="Manual Payments"
              rows={manualPayments.map((payment) => ({
                key: payment.id || payment.reference || `${payment.date}-${payment.amount}`,
                date: payment.date,
                provider: payment.provider,
                text: payment.description || payment.reference || 'Manual payment',
                amount: payment.amount,
                removable: true,
              }))}
              deletingId={deletingCashId}
              pendingDeleteId={pendingCashDeleteId}
              onRequestDelete={onRequestCashDelete}
              onCancelDelete={onCancelCashDelete}
              onConfirmDelete={onConfirmCashDelete}
            />
          </section>

          <section className="rounded-2xl border border-amber-200 bg-amber-50 p-3">
            <h3 className="text-sm font-black text-amber-950">Add manual payment</h3>
            <p className="mt-1 text-xs font-semibold text-amber-800">Use this for cash or other payments that did not pass through BOG/TBC.</p>

            <div className="mt-3 space-y-3">
              <label className="block text-[11px] font-bold uppercase tracking-wide text-amber-900">
                Payment Date
                <input
                  type="date"
                  name="supplier-debt-modal-cash-date"
                  autoComplete="off"
                  value={form.date}
                  onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
                  className="mt-1 min-h-12 w-full rounded-xl border border-amber-200 bg-white px-3 text-xs font-semibold text-slate-900 transition focus-visible:border-amber-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100 sm:h-10 sm:min-h-0"
                />
              </label>

              <label className="block text-[11px] font-bold uppercase tracking-wide text-amber-900">
                Amount
                <input
                  type="number"
                  name="supplier-debt-modal-cash-amount"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  autoComplete="off"
                  value={form.amount}
                  onChange={(event) => setForm((current) => ({ ...current, amount: event.target.value }))}
                  className="mt-1 min-h-12 w-full rounded-xl border border-amber-200 bg-white px-3 text-xs font-semibold text-slate-900 transition focus-visible:border-amber-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100 sm:h-10 sm:min-h-0"
                  placeholder="0.00"
                />
              </label>

              <label className="block text-[11px] font-bold uppercase tracking-wide text-amber-900">
                Note
                <textarea
                  name="supplier-debt-modal-cash-note"
                  autoComplete="off"
                  value={form.note}
                  onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))}
                  className="mt-1 min-h-24 w-full rounded-xl border border-amber-200 bg-white px-3 py-2 text-xs font-medium text-slate-900 transition focus-visible:border-amber-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100"
                  placeholder="Receipt number, cashier note, or context..."
                />
              </label>
            </div>

            {saveError ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-xs font-semibold text-red-700">{saveError}</p> : null}

            <button
              type="button"
              disabled={!canSave || isSaving}
              onClick={onSave}
              className="mt-4 inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 text-xs font-black text-white transition-colors hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-10"
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              {isSaving ? 'Saving...' : 'Save Manual Payment'}
            </button>
          </section>
        </div>
      </div>
    </div>
  )
}

function _CashPaymentPanel({
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
    <aside className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex items-center gap-2">
        <Wallet className="h-5 w-5 text-amber-600" aria-hidden="true" />
        <h2 className="text-base font-black text-slate-950 sm:text-lg">Add Cash Payment</h2>
      </div>
      <p className="mt-2 text-[13px] text-slate-500 sm:text-sm">Record manual supplier payments that did not pass through BOG or TBC.</p>

      <div className="mt-4 space-y-3 sm:mt-5 sm:space-y-4">
        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Supplier
          <select
            name="supplier-debt-cash-supplier"
            autoComplete="off"
            className="mt-1 h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
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
            name="supplier-debt-cash-date"
            autoComplete="off"
            value={form.date}
            onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
            className="mt-1 h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-semibold transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
          />
        </label>

        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Amount
          <input
            type="number"
            name="supplier-debt-cash-amount"
            min="0"
            step="0.01"
            inputMode="decimal"
            autoComplete="off"
            value={form.amount}
            onChange={(event) => setForm((current) => ({ ...current, amount: event.target.value }))}
            className="mt-1 h-11 w-full rounded-xl border border-slate-200 px-3 text-sm font-semibold transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
            placeholder="0.00…"
          />
        </label>

        <label className="block text-xs font-bold uppercase tracking-wide text-slate-500">
          Note
          <textarea
            name="supplier-debt-cash-note"
            autoComplete="off"
            value={form.note}
            onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))}
            className="mt-1 min-h-24 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100"
            placeholder="Optional receipt number, cashier note, or context…"
          />
        </label>
      </div>

      {error ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-[13px] font-semibold text-red-700 sm:text-sm">{error}</p> : null}

      <button
        type="button"
        disabled={!canSave || isSaving}
        onClick={onSave}
        className="mt-5 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-2 text-[13px] font-black text-white transition-colors hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-50 sm:text-sm"
      >
        <Plus className="h-4 w-4" aria-hidden="true" />
        {isSaving ? 'Saving…' : 'Save Cash Payment'}
      </button>
    </aside>
  )
}

function SupplierDebtDetails({
  supplier,
  isLoading,
  error,
  deletingCashId,
  pendingCashDeleteId,
  onRequestCashDelete,
  onCancelCashDelete,
  onConfirmCashDelete,
}: SupplierDebtDetailsContentProps) {
  return (
    <tr>
      <td colSpan={9} className="bg-slate-50 px-4 py-4">
        {isLoading ? <p className="mb-3 text-sm font-semibold text-slate-500">Loading supplier transactions…</p> : null}
        {error ? <p className="mb-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">{error}</p> : null}
        <div className="grid gap-4 lg:grid-cols-2">
          <DetailList
            title="RS.ge Purchases"
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
            pendingDeleteId={pendingCashDeleteId}
            onRequestDelete={onRequestCashDelete}
            onCancelDelete={onCancelCashDelete}
            onConfirmDelete={onConfirmCashDelete}
          />
        </div>
      </td>
    </tr>
  )
}

function SupplierDebtDetailsContent({
  supplier,
  isLoading,
  error,
  deletingCashId,
  pendingCashDeleteId,
  onRequestCashDelete,
  onCancelCashDelete,
  onConfirmCashDelete,
}: SupplierDebtDetailsContentProps) {
  return (
    <>
      {isLoading ? <p className="mb-3 text-[13px] font-semibold text-slate-500 sm:text-sm">Loading supplier transactions...</p> : null}
      {error ? <p className="mb-3 rounded-xl border border-red-200 bg-red-50 p-3 text-[13px] font-semibold text-red-700 sm:text-sm">{error}</p> : null}
      <div className="grid gap-3 lg:grid-cols-2">
        <DetailList
          title="RS.ge Purchases"
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
          pendingDeleteId={pendingCashDeleteId}
          onRequestDelete={onRequestCashDelete}
          onCancelDelete={onCancelCashDelete}
          onConfirmDelete={onConfirmCashDelete}
        />
      </div>
    </>
  )
}

function DetailList({
  title,
  rows,
  deletingId,
  pendingDeleteId,
  onRequestDelete,
  onCancelDelete,
  onConfirmDelete,
}: {
  title: string
  rows: { key: string; date: string | null; provider: string; text: string; amount: number; removable?: boolean }[]
  deletingId?: string | null
  pendingDeleteId?: string | null
  onRequestDelete?: (id: string) => void
  onCancelDelete?: () => void
  onConfirmDelete?: (id: string) => void
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-3 py-2 text-sm font-bold text-slate-700">{title}</div>
      <div className="max-h-72 overflow-auto divide-y divide-slate-100">
        {rows.map((row, index) => (
          <div key={`${row.key}-${index}`} className="grid gap-1 px-3 py-2 text-[11px] sm:grid-cols-[90px_64px_1fr_120px_auto] sm:items-center sm:gap-2 sm:text-xs">
            <span className="text-slate-500">{row.date || '-'}</span>
            <span className="w-fit rounded-full bg-slate-100 px-2 py-1 text-center font-black text-slate-600">{row.provider}</span>
            <span className="min-w-0 truncate text-slate-700">{row.text || '-'}</span>
            <span className="font-bold tabular-nums sm:text-right">{formatGel(row.amount)}</span>
            {row.removable && onRequestDelete && onConfirmDelete && onCancelDelete ? (
              pendingDeleteId === row.key ? (
                <span className="inline-flex flex-wrap items-center gap-1 sm:justify-end">
                  <button
                    type="button"
                    className="min-h-12 rounded-lg bg-red-600 px-3 text-[11px] font-black text-white transition-colors hover:bg-red-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-red-100 disabled:opacity-50 sm:h-8 sm:min-h-0 sm:px-2"
                    disabled={deletingId === row.key}
                    onClick={() => onConfirmDelete(row.key)}
                  >
                    Confirm
                  </button>
                  <button
                    type="button"
                    className="min-h-12 rounded-lg px-3 text-[11px] font-black text-slate-600 transition-colors hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-slate-100 sm:h-8 sm:min-h-0 sm:px-2"
                    onClick={onCancelDelete}
                  >
                    Cancel
                  </button>
                </span>
              ) : (
                <button
                  type="button"
                  className="inline-flex min-h-12 w-12 items-center justify-center rounded-lg text-red-600 transition-colors hover:bg-red-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-red-100 disabled:opacity-50 sm:h-8 sm:min-h-0 sm:w-8"
                  disabled={deletingId === row.key}
                  onClick={() => onRequestDelete(row.key)}
                  aria-label="Delete cash payment"
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </button>
              )
            ) : (
              <span className="hidden h-8 w-8 sm:block" />
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
    <section className="rounded-3xl border border-amber-200 bg-amber-50 p-4 shadow-sm sm:p-5">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-base font-black text-amber-950 sm:text-lg">Unmatched Bank Debit Groups</h2>
          <p className="mt-1 text-[13px] text-amber-800 sm:text-sm">
            Groups reuse provider + TIN/account/name/description. One mapping fixes every repeated transaction in that group.
          </p>
        </div>
        <span className="rounded-full bg-amber-200 px-3 py-1 text-xs font-black text-amber-950">{groups.length} open groups</span>
      </div>

      {error ? <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-[13px] font-semibold text-red-700 sm:text-sm">{error}</p> : null}

      <div className="mt-4 space-y-3">
        {groups.map((group) => {
          const key = group.groupKey
          const selectedSupplier = suppliers.find((supplier) => supplier.supplierKey === mappingDrafts[key])
          return (
            <div key={key} className="rounded-2xl border border-amber-200 bg-white p-3 sm:p-4">
              <div className="grid gap-3 lg:grid-cols-[1fr_260px_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-slate-950 px-2 py-1 text-[11px] font-black text-white">{group.provider}</span>
                    <span className="rounded-full bg-amber-100 px-2 py-1 text-[11px] font-black text-amber-900">{group.matchType}</span>
                    <p className="min-w-0 truncate text-[13px] font-black text-slate-900 sm:text-sm">{group.counterparty || group.matchText || 'Unknown counterparty'}</p>
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
                  name={`supplier-debt-mapping-${group.groupKey}`}
                  autoComplete="off"
                  className="h-11 rounded-xl border border-slate-200 bg-white px-3 text-[13px] font-semibold transition focus-visible:border-cyan-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-cyan-100 sm:text-sm"
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
                  className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-2 text-[13px] font-black text-white transition-colors hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-50 sm:text-sm"
                  disabled={!selectedSupplier || savingMapping}
                  onClick={() => selectedSupplier && onSaveMapping(group, selectedSupplier)}
                >
                  <Save className="h-4 w-4" aria-hidden="true" />
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
