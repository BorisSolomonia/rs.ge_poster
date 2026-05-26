import { useMemo, useState } from 'react'
import type React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, Save, Search } from 'lucide-react'
import { deleteBankMapping, getBogBankAnalysis, getTbcBankAnalysis, listBankMappings, saveBankMapping } from '../api/bank-analysis.api'
import { ApiClientError } from '../api/client'
import { formatGel, getDefaultDateRange } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type { BankDirection, BankTransactionMapping, BankUnmappedGroup } from '../types'

const inputClass = 'min-h-12 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 sm:h-10 sm:min-h-0'
type BankProvider = 'TBC' | 'BOG'

export default function BankAnalysisPage() {
  const defaults = getDefaultDateRange()
  const [dateFrom, setDateFrom] = useState(defaults.from)
  const [dateTo, setDateTo] = useState(defaults.to)
  const [provider, setProvider] = useState<BankProvider>('TBC')
  const [submittedRange, setSubmittedRange] = useState<{ provider: BankProvider; dateFrom: string; dateTo: string } | null>(null)
  const [mappingDrafts, setMappingDrafts] = useState<Record<string, string>>({})
  const queryClient = useQueryClient()

  const analysisQuery = useQuery({
    queryKey: ['bank-analysis', submittedRange?.provider, submittedRange?.dateFrom, submittedRange?.dateTo],
    queryFn: () => submittedRange!.provider === 'BOG'
      ? getBogBankAnalysis(submittedRange!.dateFrom, submittedRange!.dateTo)
      : getTbcBankAnalysis(submittedRange!.dateFrom, submittedRange!.dateTo),
    enabled: Boolean(submittedRange),
  })

  const mappingsQuery = useQuery({
    queryKey: ['bank-analysis-mappings'],
    queryFn: listBankMappings,
  })

  const saveMappingMutation = useMutation({
    mutationFn: ({ direction, matchText, category }: { direction: BankDirection; matchText: string; category: string }) =>
      saveBankMapping(direction, matchText, category),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['bank-analysis-mappings'] })
      await queryClient.invalidateQueries({ queryKey: ['bank-analysis'] })
    },
  })

  const deleteMappingMutation = useMutation({
    mutationFn: deleteBankMapping,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['bank-analysis-mappings'] })
      await queryClient.invalidateQueries({ queryKey: ['bank-analysis'] })
    },
  })



  const overview = analysisQuery.data

  const groupedTotals = useMemo(() => {
    const totals = overview?.categoryTotals ?? []
    return {
      credits: totals.filter((total) => total.direction === 'CREDIT'),
      debits: totals.filter((total) => total.direction === 'DEBIT'),
    }
  }, [overview])

  function runAnalysis(event: React.FormEvent) {
    event.preventDefault()
    setSubmittedRange({ provider, dateFrom, dateTo })
  }

  function draftKey(direction: BankDirection, matchText: string) {
    return `${direction}:${matchText}`
  }

  function saveGroup(group: BankUnmappedGroup) {
    const category = mappingDrafts[draftKey(group.direction, group.matchText)]?.trim()
    if (!category) {
      return
    }
    saveMappingMutation.mutate({
      direction: group.direction,
      matchText: group.matchText,
      category,
    })
  }

  return (
    <div className="space-y-4 px-3 py-4 sm:space-y-6 sm:p-6">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-950">{env.bankAnalysisTitle}</h1>
            <p className="mt-2 max-w-3xl text-sm text-slate-600">{env.bankAnalysisInfo}</p>
          </div>
          <form onSubmit={runAnalysis} className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <Field label={env.bankAnalysisProviderLabel}>
              <select className={inputClass} value={provider} onChange={(event) => setProvider(event.target.value as BankProvider)}>
                <option value="TBC">{env.bankAnalysisTbcLabel}</option>
                <option value="BOG">{env.bankAnalysisBogLabel}</option>
              </select>
            </Field>
            <Field label={env.reconcileDateFromLabel}>
              <input className={inputClass} type="date" value={dateFrom} onChange={(event) => setDateFrom(event.target.value)} />
            </Field>
            <Field label={env.reconcileDateToLabel}>
              <input className={inputClass} type="date" value={dateTo} onChange={(event) => setDateTo(event.target.value)} />
            </Field>
            <button
              className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-lg bg-slate-950 px-4 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50 sm:h-10 sm:min-h-0 sm:w-auto"
              disabled={analysisQuery.isFetching}
            >
              <Search className="h-4 w-4" />
              {analysisQuery.isFetching ? env.bankAnalysisRunningLabel : `${env.bankAnalysisRunLabel} (${provider})`}
            </button>
          </form>
        </div>
      </section>
      {analysisQuery.error ? (
        <ApiErrorNotice error={analysisQuery.error} provider={submittedRange?.provider} />
      ) : null}

      {overview ? (
        <>
          <section className="grid gap-4 md:grid-cols-4">
            <MetricCard label={env.bankAnalysisCreditsLabel} value={formatGel(overview.totalCredits)} />
            <MetricCard label={env.bankAnalysisDebitsLabel} value={formatGel(overview.totalDebits)} />
            <MetricCard label={env.bankAnalysisNetLabel} value={formatGel(overview.netMovement)} tone={overview.netMovement < 0 ? 'bad' : 'good'} />
            <MetricCard label={env.bankAnalysisTransactionsLabel} value={overview.transactionCount.toString()} />
          </section>

          <section className="grid gap-6 xl:grid-cols-2">
            <UnmappedPanel
              title={env.bankAnalysisLargeCreditsLabel}
              groups={overview.largeUnmappedCredits}
              mappingDrafts={mappingDrafts}
              setMappingDrafts={setMappingDrafts}
              onSave={saveGroup}
              saving={saveMappingMutation.isPending}
              draftKey={draftKey}
            />
            <UnmappedPanel
              title={env.bankAnalysisDebitReceiversLabel}
              groups={overview.unmappedDebitReceivers}
              mappingDrafts={mappingDrafts}
              setMappingDrafts={setMappingDrafts}
              onSave={saveGroup}
              saving={saveMappingMutation.isPending}
              draftKey={draftKey}
            />
          </section>

          <section className="grid gap-6 xl:grid-cols-2">
            <TotalsPanel title={`${env.bankAnalysisCategoryTotalsLabel} - ${env.bankAnalysisCreditsLabel}`} totals={groupedTotals.credits} />
            <TotalsPanel title={`${env.bankAnalysisCategoryTotalsLabel} - ${env.bankAnalysisDebitsLabel}`} totals={groupedTotals.debits} />
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-950">{env.bankAnalysisTransactionsLabel}</h2>
            <div className="mt-4 max-h-[520px] overflow-auto rounded-xl border border-slate-100">
              <table className="min-w-full divide-y divide-slate-100 text-sm">
                <thead className="sticky top-0 bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Date</th>
                    <th className="px-4 py-3">Direction</th>
                    <th className="px-4 py-3 text-right">Amount</th>
                    <th className="px-4 py-3">Counterparty</th>
                    <th className="px-4 py-3">Description</th>
                    <th className="px-4 py-3">Category</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {overview.transactions.map((transaction, index) => (
                    <tr key={`${transaction.date}-${transaction.reference}-${index}`} className={transaction.mapped ? '' : 'bg-amber-50/40'}>
                      <td className="px-4 py-3 text-slate-700">{transaction.date}</td>
                      <td className="px-4 py-3 text-slate-700">{transaction.direction}</td>
                      <td className="px-4 py-3 text-right font-semibold tabular-nums text-slate-900">{formatGel(transaction.amount)}</td>
                      <td className="max-w-xs truncate px-4 py-3 text-slate-700">{transaction.counterparty || '-'}</td>
                      <td className="max-w-md truncate px-4 py-3 text-slate-500">{transaction.description || '-'}</td>
                      <td className="px-4 py-3 text-slate-700">{transaction.category}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      ) : null}

      <MappingsPanel
        mappings={mappingsQuery.data ?? []}
        onDelete={(id) => deleteMappingMutation.mutate(id)}
        deleting={deleteMappingMutation.isPending}
      />
    </div>
  )
}

function ApiErrorNotice({ error, provider }: { error: unknown; provider?: BankProvider }) {
  const apiError = error instanceof ApiClientError ? error : null
  const message = error instanceof Error ? error.message : 'Unknown error'
  const responseDetails = apiError?.details && typeof apiError.details === 'object'
    ? (apiError.details as { technicalDetails?: unknown })
    : null
  const technicalDetails = typeof responseDetails?.technicalDetails === 'string' ? responseDetails.technicalDetails : ''
  const rawPayload = apiError?.details ? JSON.stringify(apiError.details, null, 2) : ''
  const details = technicalDetails || rawPayload
  return (
    <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
      <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
      <div className="min-w-0">
        <p>{message}</p>
        <div className="mt-2 flex flex-wrap gap-2 text-xs text-amber-800">
          {provider ? <span className="rounded-full bg-white/70 px-2 py-1">Provider: {provider}</span> : null}
          {apiError?.status ? <span className="rounded-full bg-white/70 px-2 py-1">HTTP: {apiError.status}</span> : null}
          {apiError?.code ? <span className="rounded-full bg-white/70 px-2 py-1">Code: {apiError.code}</span> : null}
          {apiError?.timestamp ? <span className="rounded-full bg-white/70 px-2 py-1">Backend: {apiError.timestamp}</span> : null}
        </div>
        {details ? (
          <details className="mt-3 rounded-xl border border-amber-300 bg-white/70 p-3 text-xs">
            <summary className="cursor-pointer font-bold uppercase tracking-wide">Show Full Trace</summary>
            <pre className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-slate-950 p-3 font-mono text-[11px] leading-5 text-slate-100">
              {details}
            </pre>
          </details>
        ) : null}
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block text-sm font-medium text-slate-700">
      <span className="mb-1 block">{label}</span>
      {children}
    </label>
  )
}

function MetricCard({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' }) {
  const color = tone === 'good' ? 'text-emerald-700' : tone === 'bad' ? 'text-amber-700' : 'text-slate-950'
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-sm font-medium text-slate-500">{label}</p>
      <p className={`mt-3 text-2xl font-bold tabular-nums ${color}`}>{value}</p>
    </div>
  )
}

function UnmappedPanel({
  title,
  groups,
  mappingDrafts,
  setMappingDrafts,
  onSave,
  saving,
  draftKey,
}: {
  title: string
  groups: BankUnmappedGroup[]
  mappingDrafts: Record<string, string>
  setMappingDrafts: React.Dispatch<React.SetStateAction<Record<string, string>>>
  onSave: (group: BankUnmappedGroup) => void
  saving: boolean
  draftKey: (direction: BankDirection, matchText: string) => string
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
      <div className="mt-4 space-y-3">
        {groups.length === 0 ? <p className="text-sm text-slate-500">No unmapped transactions for this range.</p> : null}
        {groups.map((group) => {
          const key = draftKey(group.direction, group.matchText)
          return (
            <div key={key} className="rounded-xl border border-slate-100 bg-slate-50 p-4">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-slate-900">{group.matchText || 'Unknown counterparty'}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {group.transactionCount} tx • {formatGel(group.amount)} total • largest {formatGel(group.largestTransaction)}
                  </p>
                  {group.description ? <p className="mt-1 line-clamp-2 text-xs text-slate-500">{group.description}</p> : null}
                </div>
                <div className="flex w-full min-w-0 flex-col gap-2 sm:min-w-[280px] sm:flex-row lg:w-auto">
                  <input
                    className={inputClass}
                    placeholder={env.bankAnalysisMappingCategoryPlaceholder}
                    value={mappingDrafts[key] ?? ''}
                    onChange={(event) =>
                      setMappingDrafts((current) => ({ ...current, [key]: event.target.value }))
                    }
                  />
                  <button
                    className="inline-flex min-h-12 items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 sm:h-10 sm:min-h-0"
                    disabled={saving || !(mappingDrafts[key] ?? '').trim()}
                    onClick={() => onSave(group)}
                  >
                    <Save className="h-4 w-4" />
                    {env.bankAnalysisSaveMappingLabel}
                  </button>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function TotalsPanel({ title, totals }: { title: string; totals: { category: string; amount: number; transactionCount: number }[] }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
      <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
        <table className="min-w-full divide-y divide-slate-100 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Category</th>
              <th className="px-4 py-3 text-right">Amount</th>
              <th className="px-4 py-3 text-right">Count</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {totals.map((total) => (
              <tr key={total.category}>
                <td className="px-4 py-3 text-slate-700">{total.category}</td>
                <td className="px-4 py-3 text-right font-semibold tabular-nums text-slate-900">{formatGel(total.amount)}</td>
                <td className="px-4 py-3 text-right tabular-nums text-slate-700">{total.transactionCount}</td>
              </tr>
            ))}
            {totals.length === 0 ? (
              <tr>
                <td className="px-4 py-4 text-sm text-slate-500" colSpan={3}>No totals yet.</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function MappingsPanel({
  mappings,
  onDelete,
  deleting,
}: {
  mappings: BankTransactionMapping[]
  onDelete: (id: string) => void
  deleting: boolean
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-950">Saved Bank Mappings</h2>
      <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
        <table className="min-w-full divide-y divide-slate-100 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Direction</th>
              <th className="px-4 py-3">Match Text</th>
              <th className="px-4 py-3">Category</th>
              <th className="px-4 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {mappings.map((mapping) => (
              <tr key={mapping.id}>
                <td className="px-4 py-3 text-slate-700">{mapping.direction}</td>
                <td className="px-4 py-3 text-slate-700">{mapping.matchText}</td>
                <td className="px-4 py-3 text-slate-700">{mapping.category}</td>
                <td className="px-4 py-3 text-right">
                  <button
                    className="text-sm font-semibold text-red-600 hover:text-red-700 disabled:opacity-50"
                    disabled={deleting}
                    onClick={() => onDelete(mapping.id)}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
            {mappings.length === 0 ? (
              <tr>
                <td className="px-4 py-4 text-sm text-slate-500" colSpan={4}>No saved mappings yet.</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  )
}
