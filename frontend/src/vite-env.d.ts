/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly [key: string]: string | undefined
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_VERSION: string
  readonly VITE_ROUTER_BASENAME: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_PREFIX: string
  readonly VITE_ASSET_BASE: string
  readonly VITE_ROUTE_RECONCILE_PURCHASES: string
  readonly VITE_NAV_RECONCILE_PURCHASES_LABEL: string
  readonly VITE_DEV_PORT: string
  readonly VITE_DEV_PROXY_TARGET: string
  readonly VITE_QUERY_RETRY: string
  readonly VITE_QUERY_STALE_TIME_MS: string
  readonly VITE_RESULTS_POLL_INTERVAL_MS: string
  readonly VITE_MATCH_THRESHOLD: string
  readonly VITE_RECONCILE_PURCHASES_TITLE: string
  readonly VITE_RECONCILE_PURCHASES_UPLOAD_TITLE: string
  readonly VITE_RECONCILE_PURCHASES_POSTER_LABEL: string
  readonly VITE_RECONCILE_PURCHASES_RUN_LABEL: string
  readonly VITE_RECONCILE_PURCHASES_RUNNING_LABEL: string
  readonly VITE_RECONCILE_PURCHASES_INFO: string
  readonly VITE_RECONCILE_RSGE_ACCEPT: string
  readonly VITE_RECONCILE_POSTER_ACCEPT: string
  readonly VITE_CORRECTION_GUIDE_PREFIX: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
