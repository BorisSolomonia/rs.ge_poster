import React from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { env } from '../../env'
import {
  BarChart3,
  RefreshCcw,
  Users,
  Package,
  Landmark,
  Wallet,
} from 'lucide-react'

const navItems = [
  { to: env.routeDashboard, label: env.navDashboardLabel, icon: BarChart3, end: true },
  { to: env.routeReconcile, label: env.navReconcileLabel, icon: RefreshCcw },
  { to: env.routePurchaseReconcile, label: env.navPurchaseReconcileLabel, icon: RefreshCcw },
  { to: env.routeSalesAnalysis, label: env.navSalesAnalysisLabel, icon: Landmark },
  { to: env.routeCashFlow, label: env.navCashFlowLabel, icon: Wallet },
  { to: env.routeSalesProducts, label: env.navSalesProductsLabel, icon: Package },
  { to: env.routeSupplierMappings, label: env.navSuppliersLabel, icon: Users },
  { to: env.routeProductMappings, label: env.navProductsLabel, icon: Package },
]

export default function AppLayout() {
  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-56 bg-white border-r border-gray-200 flex flex-col shadow-sm">
        <div className="h-14 flex items-center px-5 border-b border-gray-200">
          <span className="font-bold text-lg text-gray-900 tracking-tight">{env.appTitle}</span>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                }`
              }
            >
              <Icon className="w-4 h-4 flex-shrink-0" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="p-4 border-t border-gray-200">
          <p className="text-xs text-gray-400">{env.appTitle} v{env.appVersion}</p>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
