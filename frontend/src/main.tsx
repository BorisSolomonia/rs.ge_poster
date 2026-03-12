import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppLayout from './components/layout/AppLayout'
import DashboardPage from './pages/DashboardPage'
import ReconcilePage from './pages/ReconcilePage'
import PurchaseReconcilePage from './pages/PurchaseReconcilePage'
import SalesAnalysisPage from './pages/SalesAnalysisPage'
import SalesProductsPage from './pages/SalesProductsPage'
import SupplierMappingPage from './pages/SupplierMappingPage'
import ProductMappingPage from './pages/ProductMappingPage'
import { env } from './env'
import './index.css'

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
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path={env.routeDashboard} element={<DashboardPage />} />
            <Route path={env.routeReconcile} element={<ReconcilePage />} />
            <Route path={env.routePurchaseReconcile} element={<PurchaseReconcilePage />} />
            <Route path={env.routeSalesAnalysis} element={<SalesAnalysisPage />} />
            <Route path={env.routeSalesProducts} element={<SalesProductsPage />} />
            <Route path={env.routeSupplierMappings} element={<SupplierMappingPage />} />
            <Route path={env.routeProductMappings} element={<ProductMappingPage />} />
            <Route path="*" element={<Navigate to={env.routeDashboard} replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
)
