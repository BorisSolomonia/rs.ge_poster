import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as suppliersApi from '../api/suppliers.api'
import * as productsApi from '../api/products.api'
import { env } from '../env'
import type { SupplierMapping, ProductMapping } from '../types'
import { Plus, Trash2, TestTube2, Check, X } from 'lucide-react'
import ConfirmDialog from '../components/common/ConfirmDialog'

export default function ProductMappingPage() {
  const qc = useQueryClient()
  const [selectedSupplierId, setSelectedSupplierId] = useState<string>('')
  const [showAdd, setShowAdd] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [testPattern, setTestPattern] = useState('')
  const [testValue, setTestValue] = useState('')
  const [testIsRegex, setTestIsRegex] = useState(false)

  const { data: suppliers } = useQuery({
    queryKey: ['supplier-mappings'],
    queryFn: suppliersApi.getAll,
  })

  const { data: productMappings } = useQuery({
    queryKey: ['product-mappings', selectedSupplierId],
    queryFn: () => productsApi.getProductMappings(selectedSupplierId),
    enabled: !!selectedSupplierId,
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => productsApi.deleteProductMapping(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['product-mappings', selectedSupplierId] }),
  })

  const testMut = useMutation({
    mutationFn: () => productsApi.testPattern({ pattern: testPattern, testValue, isRegex: testIsRegex }),
  })

  const selectedSupplier = suppliers?.find((s) => s.id === selectedSupplierId)

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{env.productsTitle}</h1>

      {/* Supplier selector */}
      <div className="mb-5">
        <label className="text-xs font-medium text-gray-600 block mb-1">{env.productsSelectSupplierLabel}</label>
        <select
          value={selectedSupplierId}
          onChange={(e) => setSelectedSupplierId(e.target.value)}
          className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-64"
        >
          <option value="">{env.productsSelectSupplierPlaceholder}</option>
          {suppliers?.map((s: SupplierMapping) => (
            <option key={s.id} value={s.id}>
              {s.posterAlias} ↔ {s.rsgeOfficialName}
            </option>
          ))}
        </select>
      </div>

      {selectedSupplier && (
        <>
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden mb-6">
            <div className="flex items-center justify-between px-5 py-3 border-b border-gray-100 bg-gray-50">
              <h2 className="font-semibold text-gray-700 text-sm">
                {env.productsMappingsForPrefix} {selectedSupplier.posterAlias}
              </h2>
              <button
                onClick={() => setShowAdd(true)}
                className="flex items-center gap-1.5 text-xs px-3 py-1.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                <Plus className="w-3.5 h-3.5" /> {env.productsAddPatternLabel}
              </button>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">rs.ge Pattern</th>
                  <th className="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Poster Pattern</th>
                  <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Regex</th>
                  <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Priority</th>
                  <th className="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase">Excluded</th>
                  <th className="px-4 py-2.5 text-center"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {productMappings?.map((pm: ProductMapping) => (
                  <tr key={pm.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs text-gray-900">{pm.rsgeProductPattern}</td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-900">{pm.posterProductPattern}</td>
                    <td className="px-4 py-3 text-center">
                      {pm.isRegex
                        ? <Check className="w-4 h-4 text-green-600 mx-auto" />
                        : <X className="w-4 h-4 text-gray-300 mx-auto" />}
                    </td>
                    <td className="px-4 py-3 text-center text-gray-600">{pm.priority}</td>
                    <td className="px-4 py-3 text-center">
                      {pm.isExcluded
                        ? <X className="w-4 h-4 text-red-500 mx-auto" />
                        : <Check className="w-4 h-4 text-green-500 mx-auto" />}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <button onClick={() => setDeleteId(pm.id)} className="text-gray-400 hover:text-red-600">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
                {(!productMappings || productMappings.length === 0) && (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-gray-400 text-sm">
                      {env.productsNoMappingsLabel}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Pattern test panel */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
            <h2 className="font-semibold text-gray-700 text-sm mb-4 flex items-center gap-2">
              <TestTube2 className="w-4 h-4" />
              {env.productsPatternTestTitle}
            </h2>
            <div className="flex flex-wrap gap-3 items-end">
              <div>
                <label className="text-xs text-gray-600 block mb-1">{env.productsPatternLabel}</label>
                <input
                  value={testPattern}
                  onChange={(e) => setTestPattern(e.target.value)}
                  placeholder="e.g. კოლა.*"
                  className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-52"
                />
              </div>
              <div>
                <label className="text-xs text-gray-600 block mb-1">{env.productsTestValueLabel}</label>
                <input
                  value={testValue}
                  onChange={(e) => setTestValue(e.target.value)}
                  placeholder="e.g. კოკა-კოლა"
                  className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-52"
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="testRegex"
                  checked={testIsRegex}
                  onChange={(e) => setTestIsRegex(e.target.checked)}
                  className="w-4 h-4"
                />
                <label htmlFor="testRegex" className="text-xs text-gray-600">{env.productsRegexLabel}</label>
              </div>
              <button
                onClick={() => testMut.mutate()}
                disabled={!testPattern || !testValue}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                <TestTube2 className="w-4 h-4" />
                {env.productsTestButtonLabel}
              </button>
            </div>
            {testMut.data && (
              <div className={`mt-3 p-3 rounded-lg text-sm font-medium ${
                testMut.data.matches
                  ? 'bg-green-50 text-green-800 border border-green-200'
                  : 'bg-red-50 text-red-800 border border-red-200'
              }`}>
                {testMut.data.matches ? `✓ ${env.productsPatternMatchesLabel}` : `✗ ${env.productsPatternNoMatchLabel}`}
                {testMut.data.error && <p className="text-xs mt-1 font-normal">{testMut.data.error}</p>}
              </div>
            )}
          </div>

          {showAdd && (
            <AddPatternModal
              supplierId={selectedSupplierId}
              onClose={() => setShowAdd(false)}
              onSaved={() => qc.invalidateQueries({ queryKey: ['product-mappings', selectedSupplierId] })}
            />
          )}

          <ConfirmDialog
            open={deleteId !== null}
            title={env.productsDeleteTitle}
            message={env.productsDeleteMessage}
            onConfirm={() => { deleteMut.mutate(deleteId!); setDeleteId(null) }}
            onCancel={() => setDeleteId(null)}
            danger
          />
        </>
      )}
    </div>
  )
}

function AddPatternModal({
  supplierId,
  onClose,
  onSaved,
}: {
  supplierId: string
  onClose: () => void
  onSaved: () => void
}) {
  const [rsge, setRsge] = useState('')
  const [poster, setPoster] = useState('')
  const [isRegex, setIsRegex] = useState(false)
  const [priority, setPriority] = useState(0)

  const addMut = useMutation({
    mutationFn: () => productsApi.createProductMapping({
      supplierMappingId: supplierId,
      rsgeProductPattern: rsge.trim(),
      posterProductPattern: poster.trim(),
      isRegex,
      isExcluded: false,
      priority,
    }),
    onSuccess: () => { onSaved(); onClose() },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        <h3 className="font-semibold text-gray-900 mb-4">{env.productsAddTitle}</h3>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-medium text-gray-600">{env.productsRsgePatternLabel}</label>
            <input value={rsge} onChange={(e) => setRsge(e.target.value)} className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="text-xs font-medium text-gray-600">{env.productsPosterPatternLabel}</label>
            <input value={poster} onChange={(e) => setPoster(e.target.value)} className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="flex items-center gap-4">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={isRegex} onChange={(e) => setIsRegex(e.target.checked)} className="w-4 h-4" />
              {env.productsUseRegexLabel}
            </label>
            <div>
              <label className="text-xs text-gray-600">{env.productsPriorityLabel}</label>
              <input type="number" value={priority} onChange={(e) => setPriority(Number(e.target.value))} className="ml-2 w-16 border border-gray-300 rounded-lg px-2 py-1 text-sm" />
            </div>
          </div>
        </div>
        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50">{env.suppliersCancelLabel}</button>
          <button
            onClick={() => addMut.mutate()}
            disabled={!rsge || !poster || addMut.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {addMut.isPending ? env.productsSavingLabel : env.suppliersSaveLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
