import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import { env } from './env'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const PurchaseReconcilePage = lazy(() => import('./pages/PurchaseReconcilePage'))
const SalesAnalysisPage = lazy(() => import('./pages/SalesAnalysisPage'))
const CashFlowPage = lazy(() => import('./pages/CashFlowPage'))
const BankAnalysisPage = lazy(() => import('./pages/BankAnalysisPage'))
const SupplierDebtsPage = lazy(() => import('./pages/SupplierDebtsPage'))
const SalesProductsPage = lazy(() => import('./pages/SalesProductsPage'))
const SupplierMappingPage = lazy(() => import('./pages/SupplierMappingPage'))
const ProductMappingPage = lazy(() => import('./pages/ProductMappingPage'))

export default function AppRoutes() {
  return (
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
  )
}
