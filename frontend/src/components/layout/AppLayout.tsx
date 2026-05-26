import React, { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { env } from '../../env'
import {
  BarChart3,
  RefreshCcw,
  Users,
  Package,
  Landmark,
  Wallet,
  CreditCard,
  ReceiptText,
  Menu,
  X,
} from 'lucide-react'

const navItems = [
  { to: env.routeDashboard, label: env.navDashboardLabel, icon: BarChart3, end: true },
  { to: env.routePurchaseReconcile, label: env.navPurchaseReconcileLabel, icon: RefreshCcw },
  { to: env.routeSalesAnalysis, label: env.navSalesAnalysisLabel, icon: Landmark },
  { to: env.routeCashFlow, label: env.navCashFlowLabel, icon: Wallet },
  { to: env.routeBankAnalysis, label: env.navBankAnalysisLabel, icon: CreditCard },
  { to: env.routeSupplierDebts, label: env.navSupplierDebtsLabel, icon: ReceiptText },
  { to: env.routeSalesProducts, label: env.navSalesProductsLabel, icon: Package },
  { to: env.routeSupplierMappings, label: env.navSuppliersLabel, icon: Users },
  { to: env.routeProductMappings, label: env.navProductsLabel, icon: Package },
]

export default function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,#dbeafe_0,#f8fafc_34%,#eef2f7_100%)] text-slate-950">
      <header className="sticky top-0 z-40 border-b border-white/60 bg-white/85 px-4 py-3 shadow-sm backdrop-blur-xl lg:hidden">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.28em] text-slate-500">Camora</p>
            <h1 className="text-lg font-black tracking-tight text-slate-950">{env.appTitle}</h1>
          </div>
          <button
            type="button"
            className="inline-flex h-12 w-12 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-700 shadow-sm"
            aria-label={mobileNavOpen ? 'Close navigation' : 'Open navigation'}
            onClick={() => setMobileNavOpen((open) => !open)}
          >
            {mobileNavOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </header>

      <div className="flex min-h-screen">
        <aside
          className={`fixed inset-y-0 left-0 z-50 flex w-[min(18rem,90vw)] -translate-x-full flex-col border-r border-white/70 bg-slate-950 text-white shadow-2xl transition-transform duration-200 lg:sticky lg:top-0 lg:h-screen lg:w-72 lg:translate-x-0 ${
            mobileNavOpen ? 'translate-x-0' : ''
          }`}
        >
          <div className="flex h-20 items-center justify-between border-b border-white/10 px-5">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.32em] text-sky-300">Operations</p>
              <span className="text-xl font-black tracking-tight">{env.appTitle}</span>
            </div>
            <button
              type="button"
              className="inline-flex h-12 w-12 items-center justify-center rounded-xl border border-white/10 text-slate-200 lg:hidden"
              aria-label="Close navigation"
              onClick={() => setMobileNavOpen(false)}
            >
              <X className="h-5 w-5" />
            </button>
          </div>
          <nav className="flex-1 space-y-1 overflow-y-auto p-3">
            {navItems.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                onClick={() => setMobileNavOpen(false)}
                className={({ isActive }) =>
                  `flex min-h-12 items-center gap-3 rounded-2xl px-4 py-3 text-sm font-semibold transition-all ${
                    isActive
                      ? 'bg-sky-400 text-slate-950 shadow-lg shadow-sky-950/20'
                      : 'text-slate-300 hover:bg-white/10 hover:text-white'
                  }`
                }
              >
                <Icon className="h-4 w-4 flex-shrink-0" />
                <span className="truncate">{label}</span>
              </NavLink>
            ))}
          </nav>
          <div className="border-t border-white/10 p-4">
            <p className="text-xs text-slate-400">{env.appTitle} v{env.appVersion}</p>
          </div>
        </aside>

        {mobileNavOpen ? (
          <button
            type="button"
            aria-label="Close navigation overlay"
            className="fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm lg:hidden"
            onClick={() => setMobileNavOpen(false)}
          />
        ) : null}

        <main className="min-w-0 flex-1">
          <div className="mx-auto w-full max-w-[1720px] px-3 py-4 sm:px-5 lg:px-7 lg:py-7">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
