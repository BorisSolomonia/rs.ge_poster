import React, { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { AlertCircle, Play } from 'lucide-react'
import { runPurchaseAnalysis } from '../api/reconciliation.api'
import FileDropzone from '../components/common/FileDropzone'
import ReconciliationResults from '../components/reconciliation/ReconciliationResults'
import { getDefaultDateRange } from '../components/reconciliation/reconciliation.utils'
import { env } from '../env'
import type { ReconciliationResult } from '../types'

export default function PurchaseReconcilePage() {
  const defaults = getDefaultDateRange()
  const [posterFile, setPosterFile] = useState<File | null>(null)
  const [dateFrom, setDateFrom] = useState(defaults.from)
  const [dateTo, setDateTo] = useState(defaults.to)
  const [lastRunKey, setLastRunKey] = useState<string | null>(null)

  const currentRunKey = posterFile ? `${posterFile.name}:${posterFile.size}:${dateFrom}:${dateTo}` : null
  const needsRecalculation = Boolean(currentRunKey && currentRunKey !== lastRunKey)

  const mutation = useMutation({
    mutationFn: () => runPurchaseAnalysis(posterFile!, dateFrom, dateTo),
    onSuccess: () => {
      if (currentRunKey) setLastRunKey(currentRunKey)
    },
  })

  const result: ReconciliationResult | undefined = mutation.data

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
    <div className="p-6 max-w-7xl mx-auto">
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
    </div>
  )
}
