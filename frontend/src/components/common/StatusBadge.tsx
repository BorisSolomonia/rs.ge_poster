import React from 'react'
import { env } from '../../env'
import type { ReconciliationStatus } from '../../types'

const config: Record<ReconciliationStatus, { label: string; className: string }> = {
  MATCH: {
    label: env.statusMatchLabel,
    className: 'bg-green-100 text-green-800 border border-green-200',
  },
  DISCREPANCY: {
    label: env.statusDiscrepancyLabel,
    className: 'bg-red-100 text-red-800 border border-red-200 font-bold',
  },
  MISSING_IN_POSTER: {
    label: env.statusMissingPosterLabel,
    className: 'bg-amber-100 text-amber-800 border border-amber-200',
  },
  MISSING_IN_RSGE: {
    label: env.statusMissingRsgeLabel,
    className: 'bg-blue-100 text-blue-800 border border-blue-200',
  },
}

export default function StatusBadge({ status }: { status: ReconciliationStatus }) {
  const { label, className } = config[status]
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs ${className}`}>
      {label}
    </span>
  )
}
