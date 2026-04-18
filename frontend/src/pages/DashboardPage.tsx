import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import * as reconciliationApi from '../api/reconciliation.api'
import * as suppliersApi from '../api/suppliers.api'
import { env } from '../env'
import type { ReconciliationResultSummary } from '../types'
import { RefreshCcw, Users, AlertTriangle, CheckCircle2, XCircle, Clock } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(env.dashboardDateLocale, { day: '2-digit', month: 'short', year: 'numeric' })
}

export default function DashboardPage() {
  const navigate = useNavigate()

  const { data: results } = useQuery({
    queryKey: ['reconciliation-results'],
    queryFn: reconciliationApi.listResults,
    refetchInterval: env.resultsPollIntervalMs,
  })

  const { data: supplierStatus } = useQuery({
    queryKey: ['supplier-mappings-status'],
    queryFn: suppliersApi.getStatus,
  })

  const unmappedCount =
    (supplierStatus?.unmappedPoster.length ?? 0) +
    (supplierStatus?.unmappedRsge.length ?? 0)

  const latest = results?.[0]
  const chartData = latest
    ? [
        { name: env.reconcileSummaryMatchedLabel, value: latest.summary.matched, color: env.chartMatchedColor },
        { name: env.reconcileSummaryDiscrepancyLabel, value: latest.summary.discrepancy, color: env.chartDiscrepancyColor },
        { name: env.reconcileSummaryMissingPosterLabel, value: latest.summary.missingPoster, color: env.chartMissingPosterColor },
        { name: env.reconcileSummaryMissingRsgeLabel, value: latest.summary.missingRsge, color: env.chartMissingRsgeColor },
      ]
    : []

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{env.dashboardTitle}</h1>

      {/* Unmapped suppliers alert */}
      {unmappedCount > 0 && (
        <div
          onClick={() => navigate(env.routeSupplierMappings)}
          className="mb-6 bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-center gap-3 cursor-pointer hover:bg-amber-100 transition-colors"
        >
          <AlertTriangle className="w-5 h-5 text-amber-500 flex-shrink-0" />
          <div>
            <p className="font-semibold text-amber-800 text-sm">
              {unmappedCount} unmapped supplier{unmappedCount > 1 ? 's' : ''} {env.dashboardUnmappedNeedsAttentionLabel}
            </p>
            <p className="text-xs text-amber-700 mt-0.5">{env.dashboardUnmappedClickLabel}</p>
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <QuickCard
          icon={<RefreshCcw className="w-5 h-5 text-blue-600" />}
          title={env.dashboardRunReconciliationTitle}
          desc={env.dashboardRunReconciliationDesc}
          onClick={() => navigate(env.routePurchaseReconcile)}
          color="blue"
        />
        <QuickCard
          icon={<Users className="w-5 h-5 text-purple-600" />}
          title={env.dashboardManageSuppliersTitle}
          desc={`${supplierStatus?.mapped.length ?? 0} ${env.dashboardMappingsConfiguredSuffix}`}
          onClick={() => navigate(env.routeSupplierMappings)}
          color="purple"
        />
        <QuickCard
          icon={<AlertTriangle className="w-5 h-5 text-amber-600" />}
          title={env.dashboardUnmappedSuppliersTitle}
          desc={unmappedCount > 0 ? `${unmappedCount} ${env.dashboardUnmappedNeedsAttentionLabel}` : env.dashboardAllSuppliersMappedLabel}
          onClick={() => navigate(env.routeSupplierMappings)}
          color="amber"
        />
      </div>

      {/* Latest result */}
      {latest && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
            <h2 className="font-semibold text-gray-700 text-sm mb-4">
              {env.dashboardLatestRunLabel} — {formatDate(latest.dateFrom)} → {formatDate(latest.dateTo)}
            </h2>
            <div className="grid grid-cols-2 gap-3">
              <StatCard label={env.reconcileSummaryTotalLabel} value={latest.summary.totalLines} icon={<Clock />} color="text-gray-700" />
              <StatCard label={env.reconcileSummaryMatchedLabel} value={latest.summary.matched} icon={<CheckCircle2 />} color="text-green-700" />
              <StatCard label={env.reconcileSummaryDiscrepancyLabel} value={latest.summary.discrepancy} icon={<XCircle />} color="text-red-700" />
              <StatCard label={env.reconcileSummaryMissingPosterLabel} value={latest.summary.missingPoster} icon={<AlertTriangle />} color="text-amber-700" />
            </div>
          </div>

          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
            <h2 className="font-semibold text-gray-700 text-sm mb-4">{env.dashboardBreakdownLabel}</h2>
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={chartData} barSize={40}>
                <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                  {chartData.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Recent runs list */}
      {results && results.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-5 py-3 border-b border-gray-100 bg-gray-50">
            <h2 className="font-semibold text-gray-700 text-sm">{env.dashboardRecentRunsLabel} ({env.dashboardRecentRunsWindowLabel})</h2>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Period</th>
                <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Total</th>
                <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Matched</th>
                <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Issues</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Run At</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {results.map((r: ReconciliationResultSummary) => {
                const issues = r.summary.discrepancy + r.summary.missingPoster + r.summary.missingRsge
                return (
                  <tr
                    key={r.runId}
                    onClick={() => navigate(env.routePurchaseReconcile)}
                    className="hover:bg-gray-50 cursor-pointer"
                  >
                    <td className="px-4 py-3 text-gray-900">{r.dateFrom} → {r.dateTo}</td>
                    <td className="px-4 py-3 text-center">{r.summary.totalLines}</td>
                    <td className="px-4 py-3 text-center text-green-700">{r.summary.matched}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={`font-semibold ${issues > 0 ? 'text-red-700' : 'text-green-700'}`}>
                        {issues}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      {new Date(r.generatedAt).toLocaleTimeString()}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {(!results || results.length === 0) && (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-12 text-center">
          <RefreshCcw className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 font-medium">{env.dashboardNoRunsTitle}</p>
          <p className="text-sm text-gray-400 mt-1">{env.dashboardNoRunsSubtitle}</p>
          <button
            onClick={() => navigate(env.routePurchaseReconcile)}
            className="mt-4 px-5 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700"
          >
            {env.dashboardGoToReconcileLabel}
          </button>
        </div>
      )}
    </div>
  )
}

function QuickCard({ icon, title, desc, onClick, color }: {
  icon: React.ReactNode
  title: string
  desc: string
  onClick: () => void
  color: string
}) {
  const bg: Record<string, string> = {
    blue: 'hover:border-blue-300 hover:bg-blue-50',
    purple: 'hover:border-purple-300 hover:bg-purple-50',
    amber: 'hover:border-amber-300 hover:bg-amber-50',
  }
  return (
    <button
      onClick={onClick}
      className={`bg-white rounded-xl border border-gray-200 p-5 text-left shadow-sm transition-all ${bg[color]}`}
    >
      <div className="mb-3">{icon}</div>
      <p className="font-semibold text-gray-900 text-sm">{title}</p>
      <p className="text-xs text-gray-500 mt-1">{desc}</p>
    </button>
  )
}

function StatCard({ label, value, icon, color }: {
  label: string
  value: number
  icon: React.ReactNode
  color: string
}) {
  return (
    <div className="flex items-center gap-3 p-3 rounded-lg bg-gray-50">
      <span className={`${color} opacity-70`}>{icon}</span>
      <div>
        <p className={`text-xl font-bold ${color}`}>{value}</p>
        <p className="text-xs text-gray-500">{label}</p>
      </div>
    </div>
  )
}
