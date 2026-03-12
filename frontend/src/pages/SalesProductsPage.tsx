import React, { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import * as salesProductsApi from '../api/sales-products.api'
import { env } from '../env'

export default function SalesProductsPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [manualName, setManualName] = useState('')

  const { data: products, isLoading } = useQuery({
    queryKey: ['sales-products', search],
    queryFn: () => salesProductsApi.listSalesProducts(search),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ displayName, excluded }: { displayName: string; excluded: boolean }) =>
      salesProductsApi.setSalesProductExcluded(displayName, excluded),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sales-products'] }),
  })

  const addMutation = useMutation({
    mutationFn: () => salesProductsApi.createSalesProduct(manualName, true),
    onSuccess: () => {
      setManualName('')
      qc.invalidateQueries({ queryKey: ['sales-products'] })
    },
  })

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{env.salesProductsTitle}</h1>
          <p className="mt-1 text-sm text-gray-500">Manage products that must be excluded from sales totals.</p>
        </div>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={env.salesProductsSearchPlaceholder}
            className="rounded-xl border border-gray-300 py-2 pl-9 pr-3 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
          />
        </div>
      </div>

      <div className="mb-6 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[260px] flex-1">
            <label className="mb-1 block text-xs font-medium text-gray-600">{env.salesProductsManualLabel}</label>
            <input
              value={manualName}
              onChange={(e) => setManualName(e.target.value)}
              className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
            />
          </div>
          <button
            onClick={() => addMutation.mutate()}
            disabled={!manualName.trim() || addMutation.isPending}
            className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Plus className="h-4 w-4" />
            {env.salesProductsAddLabel}
          </button>
        </div>
      </div>

      <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-gray-500">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em]">Product</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em]">{env.salesProductsSourceLabel}</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.14em]">Status</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-gray-400">Loading...</td>
              </tr>
            )}
            {!isLoading && products?.map((product) => (
              <tr key={product.normalizedName} className="border-t border-gray-100">
                <td className="px-4 py-3 text-gray-800">{product.displayName}</td>
                <td className="px-4 py-3 text-gray-500">{product.source}</td>
                <td className="px-4 py-3">
                  <label className="inline-flex cursor-pointer items-center gap-2">
                    <input
                      type="checkbox"
                      checked={product.excluded}
                      onChange={(e) => toggleMutation.mutate({ displayName: product.displayName, excluded: e.target.checked })}
                      className="h-4 w-4 rounded border-gray-300"
                    />
                    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${product.excluded ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'}`}>
                      {product.excluded ? env.salesProductsExcludedLabel : env.salesProductsIncludedLabel}
                    </span>
                  </label>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
