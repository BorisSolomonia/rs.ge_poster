import React, { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ChevronDown, ChevronRight, Play, Save, Wallet } from 'lucide-react'
import { runPurchaseAnalysis } from '../api/reconciliation.api'
import { getSupplierDebtOverview, saveSupplierPaymentMapping } from '../api/supplier-debts.api'
import FileDropzone from '../components/common/FileDropzone'
import ReconciliationResults from '../components/reconciliation/ReconciliationResults'
import { formatGel } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type { ReconciliationResult, SupplierDebtPayment, SupplierDebtRow } from '../types'

export default function PurchaseReconcilePage() {
  const queryClient = useQueryClient()
  const defaults = { from: '2025-01-01', to: new Date().toISOString().slice(0, 10) }
  const [posterFile, setPosterFile] = useState<File | null>(null)
  const [dateFrom, setDateFrom] = useState(defaults.from)
  const [dateTo, setDateTo] = useState(defaults.to)
  const [lastRunKey, setLastRunKey] = useState<string | null>(null)
  const [expandedSupplier, setExpandedSupplier] = useState<string | null>(null)
  const [mappingDrafts, setMappingDrafts] = useState<Record<string, string>>({})

  const currentRunKey = posterFile ? `${posterFile.name}:${posterFile.size}:${dateFrom}:${dateTo}` : null
  const needsRecalculation = Boolean(currentRunKey && currentRunKey !== lastRunKey)

  const mutation = useMutation({
    mutationFn: () => runPurchaseAnalysis(posterFile!, dateFrom, dateTo),
    onSuccess: () => {
      if (currentRunKey) setLastRunKey(currentRunKey)
    },
  })

  const result: ReconciliationResult | undefined = mutation.data
  const supplierDebtQuery = useQuery({
    queryKey: ['supplier-debts', dateFrom, dateTo],
    queryFn: () => getSupplierDebtOverview(dateFrom, dateTo),
  })

  const saveMappingMutation = useMutation({
    mutationFn: ({ payment, supplier }: { payment: SupplierDebtPayment; supplier: SupplierDebtRow }) =>
      saveSupplierPaymentMapping({
        provider: payment.provider || 'BOG',
        matchText: payment.counterparty || payment.counterpartyInn || payment.description || payment.reference,
        supplierKey: supplier.supplierKey,
        supplierTin: supplier.supplierTin,
        supplierName: supplier.supplierName,
      }),
    onSuccess: async () => {
      setMappingDrafts({})
      await queryClient.invalidateQueries({ queryKey: ['supplier-debts'] })
    },
  })

  const onPosterFileChange = (file: File | null) => {
    setPosterFile(file)
    mutation.reset()
  }

  const onDateFromChange = (value: string) => {
    setDateFrom(value)
    mutation.reset()
  }

  const onDateToChange = (value: string) => {
    setDateTo(value)
    mutation.reset()
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{env.reconcilePurchasesTitle}</h1>

      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6 text-sm text-blue-900">
        {env.reconcilePurchasesInfo}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 mb-6">
        <h2 className="text-base font-semibold text-gray-700 mb-4">{env.reconcilePurchasesUploadTitle}</h2>
        <div className="mb-4">
          <FileDropzone
            label={env.reconcilePurchasesPosterLabel}
            accept={env.posterAccept}
            file={posterFile}
            onChange={onPosterFileChange}
          />
        </div>

        <div className="flex flex-wrap items-end gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{env.reconcileDateFromLabel}</label>
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => onDateFromChange(e.target.value)}
              className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{env.reconcileDateToLabel}</label>
            <input
              type="date"
              value={dateTo}
              onChange={(e) => onDateToChange(e.target.value)}
              className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            onClick={() => mutation.mutate()}
            disabled={!posterFile || mutation.isPending}
            className="flex items-center gap-2 px-6 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Play className="w-4 h-4" />
            {mutation.isPending
              ? env.reconcilePurchasesRunningLabel
              : needsRecalculation
                ? 'Recalculate & Update Accounting'
                : env.reconcilePurchasesRunLabel}
          </button>
        </div>

        {posterFile && (
          <p className="mt-3 text-xs text-gray-500">
            {needsRecalculation
              ? 'File is ready. Click the button to recalculate accounting based on this upload.'
              : 'This file is already calculated for the selected dates.'}
          </p>
        )}

        {mutation.isError && (
          <div className="mt-3 flex items-center gap-2 text-red-600 text-sm bg-red-50 border border-red-200 rounded-lg p-3">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            {mutation.error instanceof Error ? mutation.error.message : 'Error'}
          </div>
        )}
      </div>

      {result && (
        <ReconciliationResults
          result={result}
          rsgePlatformLabel="rs.ge purchases"
          emptyRsgeProductsMessage="Purchase waybills were fetched from rs.ge API without item-level product details."
        />
      )}

      <SupplierDebtPanel
        loading={supplierDebtQuery.isFetching}
        error={supplierDebtQuery.error instanceof Error ? supplierDebtQuery.error.message : null}
        overview={supplierDebtQuery.data}
        expandedSupplier={expandedSupplier}
        setExpandedSupplier={setExpandedSupplier}
        mappingDrafts={mappingDrafts}
        setMappingDrafts={setMappingDrafts}
        onSaveMapping={(payment, supplier) => saveMappingMutation.mutate({ payment, supplier })}
        savingMapping={saveMappingMutation.isPending}
      />
    </div>
  )
}

function SupplierDebtPanel({
  loading,
  error,
  overview,
  expandedSupplier,
  setExpandedSupplier,
  mappingDrafts,
  setMappingDrafts,
  onSaveMapping,
  savingMapping,
}: {
  loading: boolean
  error: string | null
  overview: Awaited<ReturnType<typeof getSupplierDebtOverview>> | undefined
  expandedSupplier: string | null
  setExpandedSupplier: (key: string | null) => void
  mappingDrafts: Record<string, string>
  setMappingDrafts: React.Dispatch<React.SetStateAction<Record<string, string>>>
  onSaveMapping: (payment: SupplierDebtPayment, supplier: SupplierDebtRow) => void
  savingMapping: boolean
}) {
  const suppliers = overview?.suppliers ?? []

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Wallet className="h-5 w-5 text-sky-600" />
            <h2 className="text-xl font-bold text-slate-950">Supplier Debt Analysis</h2>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            rs.ge purchase debt minus matched BOG/TBC debit payments and cash payments for the selected dates.
          </p>
        </div>
        {loading ? <span className="text-sm font-semibold text-slate-500">Loading...</span> : null}
      </div>

      {error ? (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>
      ) : null}

      {overview ? (
        <>
          <div className="mt-5 grid gap-3 md:grid-cols-4">
            <DebtMetric label="Purchases" value={formatGel(overview.purchaseTotal)} />
            <DebtMetric label="Paid" value={formatGel(overview.paidTotal)} />
            <DebtMetric label="Debt Left" value={formatGel(overview.debtTotal)} tone={overview.debtTotal > 0 ? 'bad' : 'good'} />
            <DebtMetric label="Unmatched Bank Debits" value={formatGel(overview.unmatchedPaymentTotal)} />
          </div>

          <div className="mt-5 overflow-x-auto rounded-xl border border-slate-100">
            <table className="min-w-[980px] w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Supplier</th>
                  <th className="px-4 py-3">TIN</th>
                  <th className="px-4 py-3 text-right">Purchases</th>
                  <th className="px-4 py-3 text-right">Paid</th>
                  <th className="px-4 py-3 text-right">Debt Left</th>
                  <th className="px-4 py-3 text-right">Rows</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {suppliers.map((supplier) => {
                  const open = expandedSupplier === supplier.supplierKey
                  return (
                    <React.Fragment key={supplier.supplierKey}>
                      <tr className="cursor-pointer hover:bg-slate-50" onClick={() => setExpandedSupplier(open ? null : supplier.supplierKey)}>
                        <td className="px-4 py-3 font-semibold text-slate-900">
                          <span className="inline-flex items-center gap-2">
                            {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                            {supplier.supplierName}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-mono text-xs text-slate-500">{supplier.supplierTin || '-'}</td>
                        <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatGel(supplier.purchaseTotal)}</td>
                        <td className="px-4 py-3 text-right font-semibold tabular-nums text-emerald-700">{formatGel(supplier.paidTotal)}</td>
                        <td className={`px-4 py-3 text-right font-bold tabular-nums ${supplier.debtLeft > 0 ? 'text-red-700' : 'text-emerald-700'}`}>
                          {formatGel(supplier.debtLeft)}
                        </td>
                        <td className="px-4 py-3 text-right text-slate-500">{supplier.purchaseCount} / {supplier.paymentCount}</td>
                      </tr>
                      {open ? <SupplierDebtDetails supplier={supplier} /> : null}
                    </React.Fragment>
                  )
                })}
                {suppliers.length === 0 ? (
                  <tr>
                    <td className="px-4 py-5 text-slate-500" colSpan={6}>No supplier purchase debt found for this range.</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>

          <UnmatchedPaymentsPanel
            payments={overview.unmatchedPayments}
            suppliers={suppliers}
            mappingDrafts={mappingDrafts}
            setMappingDrafts={setMappingDrafts}
            onSaveMapping={onSaveMapping}
            savingMapping={savingMapping}
          />
        </>
      ) : null}
    </section>
  )
}

function DebtMetric({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' }) {
  const color = tone === 'good' ? 'text-emerald-700' : tone === 'bad' ? 'text-red-700' : 'text-slate-950'
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-2 text-xl font-bold tabular-nums ${color}`}>{value}</p>
    </div>
  )
}

function SupplierDebtDetails({ supplier }: { supplier: SupplierDebtRow }) {
  return (
    <tr>
      <td colSpan={6} className="bg-slate-50 px-4 py-4">
        <div className="grid gap-4 lg:grid-cols-2">
          <DetailList
            title="rs.ge Purchases"
            rows={supplier.purchases.map((purchase) => ({
              key: purchase.waybillNumber,
              date: purchase.date,
              text: purchase.waybillNumber,
              amount: purchase.amount,
            }))}
          />
          <DetailList
            title="Payments"
            rows={supplier.payments.map((payment) => ({
              key: payment.id || payment.reference || `${payment.date}-${payment.amount}`,
              date: payment.date,
              text: `${payment.provider}: ${payment.counterparty || payment.description}`,
              amount: payment.amount,
            }))}
          />
        </div>
      </td>
    </tr>
  )
}

function DetailList({ title, rows }: { title: string; rows: { key: string; date: string | null; text: string; amount: number }[] }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-3 py-2 text-sm font-semibold text-slate-700">{title}</div>
      <div className="max-h-64 overflow-auto divide-y divide-slate-100">
        {rows.map((row, index) => (
          <div key={`${row.key}-${index}`} className="grid grid-cols-[110px_1fr_120px] gap-2 px-3 py-2 text-xs">
            <span className="text-slate-500">{row.date || '-'}</span>
            <span className="truncate text-slate-700">{row.text || '-'}</span>
            <span className="text-right font-semibold tabular-nums">{formatGel(row.amount)}</span>
          </div>
        ))}
        {rows.length === 0 ? <p className="px-3 py-4 text-sm text-slate-500">No rows.</p> : null}
      </div>
    </div>
  )
}

function UnmatchedPaymentsPanel({
  payments,
  suppliers,
  mappingDrafts,
  setMappingDrafts,
  onSaveMapping,
  savingMapping,
}: {
  payments: SupplierDebtPayment[]
  suppliers: SupplierDebtRow[]
  mappingDrafts: Record<string, string>
  setMappingDrafts: React.Dispatch<React.SetStateAction<Record<string, string>>>
  onSaveMapping: (payment: SupplierDebtPayment, supplier: SupplierDebtRow) => void
  savingMapping: boolean
}) {
  return (
    <div className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4">
      <h3 className="font-semibold text-amber-950">Unmatched Bank Debits</h3>
      <p className="mt-1 text-sm text-amber-800">These bank payments are excluded from supplier paid totals until mapped.</p>
      <div className="mt-4 space-y-3">
        {payments.map((payment, index) => {
          const key = payment.reference || `${payment.date}-${payment.amount}-${index}`
          const selectedSupplier = suppliers.find((supplier) => supplier.supplierKey === mappingDrafts[key])
          return (
            <div key={key} className="rounded-lg border border-amber-200 bg-white p-3">
              <div className="grid gap-3 lg:grid-cols-[1fr_220px_auto] lg:items-center">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-slate-900">{payment.counterparty || 'Unknown counterparty'}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {payment.date || '-'} • {formatGel(payment.amount)} • {payment.counterpartyInn || payment.counterpartyAccount || payment.reference || 'no identifier'}
                  </p>
                  <p className="mt-1 truncate text-xs text-slate-500">{payment.description}</p>
                </div>
                <select
                  className="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm"
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
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-slate-950 px-3 text-sm font-semibold text-white disabled:opacity-50"
                  disabled={!selectedSupplier || savingMapping}
                  onClick={() => selectedSupplier && onSaveMapping(payment, selectedSupplier)}
                >
                  <Save className="h-4 w-4" />
                  Save Mapping
                </button>
              </div>
            </div>
          )
        })}
        {payments.length === 0 ? <p className="text-sm text-amber-800">All bank debit payments are matched.</p> : null}
      </div>
    </div>
  )
}
