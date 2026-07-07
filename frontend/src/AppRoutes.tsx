import { Component, Suspense, lazy } from 'react'
import type { ReactNode } from 'react'
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

type RouteErrorBoundaryProps = {
  children: ReactNode
}

type RouteErrorBoundaryState = {
  error: Error | null
}

class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): RouteErrorBoundaryState {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <div className="m-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-black">Page failed to render</p>
          <p className="mt-1 break-words">{this.state.error.message}</p>
          <button
            type="button"
            className="mt-3 rounded-lg bg-red-700 px-3 py-2 text-xs font-black text-white"
            onClick={() => this.setState({ error: null })}
          >
            Try Again
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

export default function AppRoutes() {
  return (
    <RouteErrorBoundary>
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
    </RouteErrorBoundary>
  )
}
