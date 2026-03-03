import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as suppliersApi from '../api/suppliers.api'
import { env } from '../env'
import type { SupplierMapping, StandaloneSupplier } from '../types'
import { Plus, Trash2, EyeOff, Check, X, AlertTriangle } from 'lucide-react'
import ConfirmDialog from '../components/common/ConfirmDialog'

function CreateMappingModal({
  open,
  onClose,
  onSave,
}: {
  open: boolean
  onClose: () => void
  onSave: (posterAlias: string, rsgeRawValue: string) => void
}) {
  const [posterAlias, setPosterAlias] = useState('')
  const [rsgeRawValue, setRsgeRawValue] = useState('')
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        <h3 className="font-semibold text-gray-900 mb-4">{env.suppliersCreateTitle}</h3>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-medium text-gray-600">{env.suppliersPosterAliasLabel}</label>
            <input
              value={posterAlias}
              onChange={(e) => setPosterAlias(e.target.value)}
              placeholder={env.suppliersPosterAliasPlaceholder}
              className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="text-xs font-medium text-gray-600">{env.suppliersRsgeRawValueLabel}</label>
            <input
              value={rsgeRawValue}
              onChange={(e) => setRsgeRawValue(e.target.value)}
              placeholder="e.g. (201948063) სს კოკა-კოლა..."
              className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50">{env.suppliersCancelLabel}</button>
          <button
            onClick={() => { onSave(posterAlias.trim(), rsgeRawValue.trim()); onClose() }}
            disabled={!posterAlias.trim() || !rsgeRawValue.trim()}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {env.suppliersSaveLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function SupplierMappingPage() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)

  const { data: status, isLoading } = useQuery({
    queryKey: ['supplier-mappings-status'],
    queryFn: suppliersApi.getStatus,
  })

  const createMut = useMutation({
    mutationFn: ({ posterAlias, rsgeRawValue }: { posterAlias: string; rsgeRawValue: string }) =>
      suppliersApi.createMapping({ posterAlias, rsgeRawValue }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['supplier-mappings-status'] }),
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => suppliersApi.deleteMapping(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['supplier-mappings-status'] }),
  })

  const togglePosterMut = useMutation({
    mutationFn: (id: string) => suppliersApi.togglePosterExcluded(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['supplier-mappings-status'] }),
  })

  const toggleRsgeMut = useMutation({
    mutationFn: (id: string) => suppliersApi.toggleRsgeExcluded(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['supplier-mappings-status'] }),
  })

  const excludeStandaloneMut = useMutation({
    mutationFn: ({ platform, name }: { platform: string; name: string }) =>
      suppliersApi.excludeStandalone(platform, name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['supplier-mappings-status'] }),
  })

  const unmappedCount = (status?.unmappedPoster.length ?? 0) + (status?.unmappedRsge.length ?? 0)

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{env.suppliersTitle}</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700"
        >
          <Plus className="w-4 h-4" />
          {env.suppliersNewMappingLabel}
        </button>
      </div>

      {unmappedCount > 0 && (
        <div className="mb-5 bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 text-amber-500 flex-shrink-0" />
          <p className="text-sm font-medium text-amber-800">
            {unmappedCount} {env.suppliersUnmappedDiscoveredSuffix.replace('%s', unmappedCount > 1 ? 's' : '')}
          </p>
        </div>
      )}

      {isLoading && <p className="text-gray-400 text-sm">{env.suppliersLoadingLabel}</p>}

      {/* Mapped suppliers table */}
      {status && (
        <>
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden mb-6">
            <div className="px-5 py-3 border-b border-gray-100 bg-gray-50">
              <h2 className="font-semibold text-gray-700 text-sm">
                {env.suppliersMappedTitle} ({status.mapped.length})
              </h2>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Poster Alias</th>
                  <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">rs.ge Official Name</th>
                  <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Tax ID</th>
                  <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Excl. Poster</th>
                  <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Excl. rs.ge</th>
                  <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {status.mapped.map((m: SupplierMapping) => (
                  <tr key={m.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">{m.posterAlias}</td>
                    <td className="px-4 py-3 text-gray-700">{m.rsgeOfficialName}</td>
                    <td className="px-4 py-3 text-gray-500 font-mono text-xs">{m.rsgeTaxId ?? '—'}</td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => togglePosterMut.mutate(m.id)}
                        className={`w-7 h-7 rounded-full flex items-center justify-center mx-auto transition-colors ${
                          m.posterExcluded
                            ? 'bg-red-100 text-red-600 hover:bg-red-200'
                            : 'bg-green-100 text-green-600 hover:bg-green-200'
                        }`}
                      >
                        {m.posterExcluded ? <EyeOff className="w-3.5 h-3.5" /> : <Check className="w-3.5 h-3.5" />}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => toggleRsgeMut.mutate(m.id)}
                        className={`w-7 h-7 rounded-full flex items-center justify-center mx-auto transition-colors ${
                          m.rsgeExcluded
                            ? 'bg-red-100 text-red-600 hover:bg-red-200'
                            : 'bg-green-100 text-green-600 hover:bg-green-200'
                        }`}
                      >
                        {m.rsgeExcluded ? <EyeOff className="w-3.5 h-3.5" /> : <Check className="w-3.5 h-3.5" />}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => setDeleteId(m.id)}
                        className="text-gray-400 hover:text-red-600 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
                {status.mapped.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-gray-400">{env.suppliersNoMappingsLabel}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Unmapped suppliers */}
          {(status.unmappedPoster.length > 0 || status.unmappedRsge.length > 0) && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <UnmappedSection
                title={env.suppliersUnmappedPosterTitle}
                items={status.unmappedPoster}
                onExclude={(name) => excludeStandaloneMut.mutate({ platform: 'POSTER', name })}
                onMap={(name) => { setPosterAliasDefault(name); setShowCreate(true) }}
              />
              <UnmappedSection
                title={env.suppliersUnmappedRsgeTitle}
                items={status.unmappedRsge}
                onExclude={(name) => excludeStandaloneMut.mutate({ platform: 'RSGE', name })}
                onMap={() => setShowCreate(true)}
              />
            </div>
          )}
        </>
      )}

      <CreateMappingModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSave={(posterAlias, rsgeRawValue) =>
          createMut.mutate({ posterAlias, rsgeRawValue })
        }
      />

      <ConfirmDialog
        open={deleteId !== null}
        title={env.suppliersDeleteTitle}
        message={env.suppliersDeleteMessage}
        onConfirm={() => { deleteMut.mutate(deleteId!); setDeleteId(null) }}
        onCancel={() => setDeleteId(null)}
        danger
      />
    </div>
  )
}

// mini helper — not used but needed for map button
function setPosterAliasDefault(_: string) {}

function UnmappedSection({
  title,
  items,
  onExclude,
  onMap,
}: {
  title: string
  items: StandaloneSupplier[]
  onExclude: (name: string) => void
  onMap: (name: string) => void
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100 bg-amber-50">
        <h2 className="font-semibold text-amber-800 text-sm">{title} ({items.length})</h2>
      </div>
      <div className="divide-y divide-gray-50">
        {items.map((s) => (
          <div key={s.name} className={`flex items-center justify-between px-4 py-3 ${s.isExcluded ? 'opacity-50' : ''}`}>
            <span className="text-sm text-gray-800 truncate flex-1">{s.name}</span>
            {!s.isExcluded && (
              <div className="flex items-center gap-2 ml-3">
                <button
                  onClick={() => onMap(s.name)}
                  className="text-xs px-2.5 py-1 rounded-md bg-blue-50 text-blue-700 hover:bg-blue-100 font-medium"
                >
                  {env.suppliersMapLabel}
                </button>
                <button
                  onClick={() => onExclude(s.name)}
                  className="text-xs px-2.5 py-1 rounded-md bg-gray-100 text-gray-600 hover:bg-gray-200 font-medium"
                >
                  {env.suppliersExcludeLabel}
                </button>
              </div>
            )}
            {s.isExcluded && (
              <span className="ml-3 text-xs text-gray-400">{env.suppliersExcludedLabel}</span>
            )}
          </div>
        ))}
        {items.length === 0 && (
          <p className="px-4 py-4 text-sm text-gray-400">{env.suppliersNoneLabel}</p>
        )}
      </div>
    </div>
  )
}
