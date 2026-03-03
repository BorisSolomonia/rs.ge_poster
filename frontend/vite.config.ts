import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '')
  const routerBasename = env.VITE_ROUTER_BASENAME || '/'
  const base = env.VITE_ASSET_BASE || (routerBasename.endsWith('/') ? routerBasename : `${routerBasename}/`)

  return {
    base,
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: Number(env.VITE_DEV_PORT || 5173),
      proxy: {
        [env.VITE_API_PREFIX || '/api/v1']: {
          target: env.VITE_DEV_PROXY_TARGET || 'http://localhost:8082',
          changeOrigin: true,
        },
      },
    },
  }
})
