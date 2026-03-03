import React, { useEffect, useState } from 'react'
import { ChevronDown, ChevronUp, Download } from 'lucide-react'
import StatusBadge from '../common/StatusBadge'
import { env } from '../../env'
import type { ReconciliationResult, ReconciliationStatus } from '../../types'
import { formatGel } from './reconciliation.utils'

type FilterTab = 'ALL' | ReconciliationStatus

const FILTER_TABS: { key: FilterTab; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'DISCREPANCY', label: 'Discrepancies' },
  { key: 'MISSING_IN_POSTER', label: 'Missing in Poster' },
  { key: 'MISSING_IN_RSGE', label: 'Missing in rs.ge' },
  { key: 'MATCH', label: 'Matched' },
]

interface Props {
  result: ReconciliationResult
  rsgePlatformLabel?: string
  emptyRsgeProductsMessage?: string
}

export default function ReconciliationResults({
  result,
  rsgePlatformLabel = env.reconcileRsgePlatformLabel,
  emptyRsgeProductsMessage,
}: Props) {
  const [activeFilter, setActiveFilter] = useState<FilterTab>('ALL')
  const [expandedRow, setExpandedRow] = useState<string | null>(null)
  const [showCorrectionGuide, setShowCorrectionGuide] = useState(false)

  useEffect(() => {
    setActiveFilter('ALL')
    setExpandedRow(null)
    setShowCorrectionGuide(false)
  }, [result.runId])

  const filteredLines = result.lines.filter(
    (line) => activeFilter === 'ALL' || line.status === activeFilter
  )
  const nonMatchLines = result.lines.filter((line) => line.status !== 'MATCH')

  function exportCorrectionGuide() {
    const lines = nonMatchLines
      .map((line) => line.correctionAction ?? '')
      .filter(Boolean)
      .join('\n\n')
    const blob = new Blob([lines], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${env.correctionGuidePrefix}-${result.dateFrom}-${result.dateTo}.txt`
    anchor.click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
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

      {(result.newSuppliersDiscovered.rsge.length > 0 ||
        result.newSuppliersDiscovered.poster.length > 0) && (
        <div className="mb-4 bg-amber-50 border border-amber-200 rounded-xl p-4">
          <p className="font-semibold text-amber-800 text-sm mb-2">
            {env.reconcileNewSuppliersTitle}
          </p>
          {result.newSuppliersDiscovered.rsge.length > 0 && (
            <p className="text-xs text-amber-700">
              <span className="font-medium">{rsgePlatformLabel}: </span>
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
                : result.lines.filter((line) => line.status === key).length})
            </span>
          </button>
        ))}
      </div>

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
                    <td className="px-4 py-3 font-medium text-gray-900">{line.posterAlias ?? '-'}</td>
                    <td className="px-4 py-3 text-gray-700 max-w-xs truncate">{line.rsgeOfficialName ?? '-'}</td>
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
                          {line.rsgeProducts.length > 0 ? (
                            <div>
                              <p className="font-semibold text-gray-700 mb-1">rs.ge products</p>
                              <p className="text-gray-600">{line.rsgeProducts.join(', ')}</p>
                            </div>
                          ) : emptyRsgeProductsMessage ? (
                            <div>
                              <p className="font-semibold text-gray-700 mb-1">rs.ge products</p>
                              <p className="text-gray-500">{emptyRsgeProductsMessage}</p>
                            </div>
                          ) : null}
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
                              <p className="text-gray-600">{line.posterDocNumbers.map((n) => `#${n}`).join(', ')}</p>
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
                onClick={(event) => {
                  event.stopPropagation()
                  exportCorrectionGuide()
                }}
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
                  <div key={`${line.rsgeRawValue ?? line.posterAlias ?? idx}`} className="flex gap-3 p-3 rounded-lg bg-yellow-50 border border-yellow-200">
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
  )
}
