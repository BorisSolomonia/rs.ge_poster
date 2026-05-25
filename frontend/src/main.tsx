import React, { Suspense, lazy } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppLayout from './components/layout/AppLayout'
import { env } from './env'
import './index.css'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const PurchaseReconcilePage = lazy(() => import('./pages/PurchaseReconcilePage'))
const SalesAnalysisPage = lazy(() => import('./pages/SalesAnalysisPage'))
const CashFlowPage = lazy(() => import('./pages/CashFlowPage'))
const BankAnalysisPage = lazy(() => import('./pages/BankAnalysisPage'))
const SupplierDebtsPage = lazy(() => import('./pages/SupplierDebtsPage'))
const SalesProductsPage = lazy(() => import('./pages/SalesProductsPage'))
const SupplierMappingPage = lazy(() => import('./pages/SupplierMappingPage'))
const ProductMappingPage = lazy(() => import('./pages/ProductMappingPage'))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: env.queryRetry,
      staleTime: env.queryStaleTimeMs,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter basename={env.routerBasename}>
        <Suspense fallback={<div className="p-6 text-sm font-semibold text-slate-600">Loading Camora...</div>}>
          <Routes>
            <Route element={<AppLayout />}>
              <Route index element={<DashboardPage />} />
              <Route path={env.routeDashboard} element={<DashboardPage />} />
              <Route path={env.routeReconcile} element={<Navigate to={env.routePurchaseReconcile} replace />} />
              <Route path={env.routePurchaseReconcile} element={<PurchaseReconcilePage />} />
              <Route path={env.routeSalesAnalysis} element={<SalesAnalysisPage />} />
              <Route path={env.routeCashFlow} element={<CashFlowPage />} />
              <Route path={env.routeBankAnalysis} element={<BankAnalysisPage />} />
              <Route path={env.routeSupplierDebts} element={<SupplierDebtsPage />} />
              <Route path={env.routeSalesProducts} element={<SalesProductsPage />} />
              <Route path={env.routeSupplierMappings} element={<SupplierMappingPage />} />
              <Route path={env.routeProductMappings} element={<ProductMappingPage />} />
              <Route path="*" element={<Navigate to={env.routeDashboard} replace />} />
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
)
