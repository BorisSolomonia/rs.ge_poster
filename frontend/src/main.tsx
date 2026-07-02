import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppRoutes from './AppRoutes'
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
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
)
