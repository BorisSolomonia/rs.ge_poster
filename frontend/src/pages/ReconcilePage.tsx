import React, { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { runAnalysis } from '../api/reconciliation.api'
import FileDropzone from '../components/common/FileDropzone'
import StatusBadge from '../components/common/StatusBadge'
import { env } from '../env'
import type { ReconciliationResult, ReconciliationStatus } from '../types'
import { Play, AlertCircle, ChevronDown, ChevronUp, Download } from 'lucide-react'

type FilterTab = 'ALL' | ReconciliationStatus

const FILTER_TABS: { key: FilterTab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'DISCREPANCY', label: 'Discrepancies' },
  { key: 'MISSING_IN_POSTER', label: 'Missing in Poster' },
  { key: 'MISSING_IN_RSGE', label: 'Missing in rs.ge' },
  { key: 'MATCH', label: 'Matched' },
]

function formatGel(val: number) {
  return `${val.toFixed(2)} ₾`
}

function getDefaultDateRange() {
  const end = new Date()
  const start = new Date()
  if (env.defaultDateRangeMode === 'current-month') start.setDate(1)
  return {
    from: start.toISOString().slice(0, 10),
    to: end.toISOString().slice(0, 10),
  }
}

export default function ReconcilePage() {
  const defaults = getDefaultDateRange()
  const [rsgeFile, setRsgeFile] = useState<File | null>(null)
  const [posterFile, setPosterFile] = useState<File | null>(null)
  const [dateFrom, setDateFrom] = useState(defaults.from)
  const [dateTo, setDateTo] = useState(defaults.to)
  const [activeFilter, setActiveFilter] = useState<FilterTab>('ALL')
  const [expandedRow, setExpandedRow] = useState<string | null>(null)
  const [showCorrectionGuide, setShowCorrectionGuide] = useState(false)

  const mutation = useMutation({
    mutationFn: () => runAnalysis(rsgeFile!, posterFile!, dateFrom, dateTo),
  })

  const result: ReconciliationResult | undefined = mutation.data

  const filteredLines = result?.lines.filter(
    (l) => activeFilter === 'ALL' || l.status === activeFilter
  ) ?? []

  const nonMatchLines = result?.lines.filter((l) => l.status !== 'MATCH') ?? []

  function exportCorrectionGuide() {
    if (!result) return
    const lines = nonMatchLines
      .map((l) => l.correctionAction ?? '')
      .filter(Boolean)
      .join('\n\n')
    const blob = new Blob([lines], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${env.correctionGuidePrefix}-${result.dateFrom}-${result.dateTo}.txt`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{env.reconcileTitle}</h1>

      {/* Upload panel */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 mb-6">
        <h2 className="text-base font-semibold text-gray-700 mb-4">{env.reconcileUploadTitle}</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <FileDropzone
            label={env.reconcileRsgeLabel}
            accept={env.rsgeAccept}
            file={rsgeFile}
            onChange={setRsgeFile}
          />
          <FileDropzone
            label={env.reconcilePosterLabel}
            accept={env.posterAccept}
            file={posterFile}
            onChange={setPosterFile}
          />
        </div>

        <div className="flex flex-wrap items-end gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{env.reconcileDateFromLabel}</label>
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
              className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{env.reconcileDateToLabel}</label>
            <input
              type="date"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
              className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            onClick={() => mutation.mutate()}
            disabled={!rsgeFile || !posterFile || mutation.isPending}
            className="flex items-center gap-2 px-6 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Play className="w-4 h-4" />
            {mutation.isPending ? env.reconcileRunningLabel : env.reconcileRunLabel}
          </button>
        </div>

        {mutation.isError && (
          <div className="mt-3 flex items-center gap-2 text-red-600 text-sm bg-red-50 border border-red-200 rounded-lg p-3">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            {mutation.error instanceof Error ? mutation.error.message : 'Error'}
          </div>
        )}
      </div>

      {/* Results */}
      {result && (
        <>
          {/* Summary cards */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-6">
            {[
              { label: env.reconcileSummaryTotalLabel, value: result.summary.totalLines, color: 'text-gray-900' },
              { label: env.reconcileSummaryMatchedLabel, value: result.summary.matched, color: 'text-green-700' },
              { label: env.reconcileSummaryDiscrepancyLabel, value: result.summary.discrepancy, color: 'text-red-700' },
              { label: env.reconcileSummaryMissingPosterLabel, value: result.summary.missingPoster, color: 'text-amber-700' },
              { label: env.reconcileSummaryMissingRsgeLabel, value: result.summary.missingRsge, color: 'text-blue-700' },
            ].map(({ label, value, color }) => (
              <div key={label} className="bg-white rounded-xl border border-gray-200 shadow-sm p-4 text-center">
                <p className={`text-2xl font-bold ${color}`}>{value}</p>
                <p className="text-xs text-gray-500 mt-1">{label}</p>
              </div>
            ))}
          </div>

          {/* New suppliers alert */}
          {(result.newSuppliersDiscovered.rsge.length > 0 ||
            result.newSuppliersDiscovered.poster.length > 0) && (
            <div className="mb-4 bg-amber-50 border border-amber-200 rounded-xl p-4">
              <p className="font-semibold text-amber-800 text-sm mb-2">
                ⚠ {env.reconcileNewSuppliersTitle}
              </p>
              {result.newSuppliersDiscovered.rsge.length > 0 && (
                <p className="text-xs text-amber-700">
                  <span className="font-medium">{env.reconcileRsgePlatformLabel}: </span>
                  {result.newSuppliersDiscovered.rsge.join(', ')}
                </p>
              )}
              {result.newSuppliersDiscovered.poster.length > 0 && (
                <p className="text-xs text-amber-700 mt-1">
                  <span className="font-medium">{env.reconcilePosterPlatformLabel}: </span>
                  {result.newSuppliersDiscovered.poster.join(', ')}
                </p>
              )}
            </div>
          )}

          {/* Filter tabs */}
          <div className="flex flex-wrap gap-1 mb-3">
            {FILTER_TABS.map(({ key, label }) => (
              <button
                key={key}
                onClick={() => setActiveFilter(key)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  activeFilter === key
                    ? 'bg-blue-600 text-white'
                    : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
                }`}
              >
                {label}{' '}
                <span className="opacity-75">
                  ({key === 'ALL'
                    ? result.lines.length
                    : result.lines.filter((l) => l.status === key).length})
                </span>
              </button>
            ))}
          </div>

          {/* Results table */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden mb-6">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wide">Poster Alias</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wide">rs.ge Name</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-gray-600 uppercase tracking-wide">rs.ge Total</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-gray-600 uppercase tracking-wide">Poster Total</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-gray-600 uppercase tracking-wide">Difference</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-600 uppercase tracking-wide">Status</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-600 uppercase tracking-wide"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredLines.map((line, idx) => {
                  const key = line.rsgeRawValue ?? line.posterAlias ?? String(idx)
                  const isExpanded = expandedRow === key
                  return (
                    <React.Fragment key={key}>
                      <tr className={`hover:bg-gray-50 ${line.status === 'DISCREPANCY' ? 'bg-red-50/30' : ''}`}>
                        <td className="px-4 py-3 font-medium text-gray-900">{line.posterAlias ?? '—'}</td>
                        <td className="px-4 py-3 text-gray-700 max-w-xs truncate">{line.rsgeOfficialName ?? '—'}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-gray-900">{formatGel(line.rsgeTotal)}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-gray-900">{formatGel(line.posterTotal)}</td>
                        <td className={`px-4 py-3 text-right tabular-nums font-semibold ${
                          Math.abs(line.diff) < env.matchThreshold ? 'text-green-700' : 'text-red-700'
                        }`}>
                          {line.diff > 0 ? '+' : ''}{formatGel(line.diff)}
                        </td>
                        <td className="px-4 py-3 text-center">
                          <StatusBadge status={line.status} />
                        </td>
                        <td className="px-4 py-3 text-center">
                          <button
                            onClick={() => setExpandedRow(isExpanded ? null : key)}
                            className="text-gray-400 hover:text-gray-700"
                          >
                            {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                          </button>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr>
                          <td colSpan={7} className="px-6 py-4 bg-gray-50 border-b">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                              {line.rsgeProducts.length > 0 && (
                                <div>
                                  <p className="font-semibold text-gray-700 mb-1">rs.ge products</p>
                                  <p className="text-gray-600">{line.rsgeProducts.join(', ')}</p>
                                </div>
                              )}
                              {line.posterProductsRaw.length > 0 && (
                                <div>
                                  <p className="font-semibold text-gray-700 mb-1">Poster products</p>
                                  <p className="text-gray-600">{line.posterProductsRaw.join(', ')}</p>
                                </div>
                              )}
                              {line.waybillNumbers.length > 0 && (
                                <div>
                                  <p className="font-semibold text-gray-700 mb-1">Waybills</p>
                                  <p className="text-gray-600">{line.waybillNumbers.join(', ')}</p>
                                </div>
                              )}
                              {line.posterDocNumbers.length > 0 && (
                                <div>
                                  <p className="font-semibold text-gray-700 mb-1">Poster docs</p>
                                  <p className="text-gray-600">{line.posterDocNumbers.map(n => `#${n}`).join(', ')}</p>
                                </div>
                              )}
                              {line.correctionAction && (
                                <div className="md:col-span-2">
                                  <p className="font-semibold text-gray-700 mb-1">Correction Action</p>
                                  <p className="text-gray-700 bg-yellow-50 border border-yellow-200 rounded p-2">
                                    {line.correctionAction}
                                  </p>
                                </div>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  )
                })}
                {filteredLines.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-gray-400">{env.reconcileNoResultsLabel}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Correction Guide */}
          {nonMatchLines.length > 0 && (
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
              <div
                className="flex items-center justify-between px-6 py-4 cursor-pointer hover:bg-gray-50"
                onClick={() => setShowCorrectionGuide(!showCorrectionGuide)}
              >
                <h2 className="font-semibold text-gray-900">
                  {env.reconcileCorrectionGuideLabel} ({nonMatchLines.length} items)
                </h2>
                <div className="flex items-center gap-3">
                  <button
                    onClick={(e) => { e.stopPropagation(); exportCorrectionGuide() }}
                    className="flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-800"
                  >
                    <Download className="w-4 h-4" />
                    {env.reconcileExportLabel}
                  </button>
                  {showCorrectionGuide ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </div>
              </div>
              {showCorrectionGuide && (
                <div className="px-6 pb-6 space-y-3 border-t border-gray-100 pt-4">
                  {nonMatchLines.map((line, idx) => (
                    line.correctionAction && (
                      <div key={idx} className="flex gap-3 p-3 rounded-lg bg-yellow-50 border border-yellow-200">
                        <span className="text-amber-500 font-bold text-sm shrink-0">{idx + 1}.</span>
                        <p className="text-sm text-gray-800">{line.correctionAction}</p>
                      </div>
                    )
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
