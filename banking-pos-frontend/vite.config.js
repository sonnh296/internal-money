import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const viaGateway = env.VITE_PROXY_VIA_GATEWAY === 'true'
  const useAuthCookies = env.VITE_AUTH_COOKIES === 'true'
  const gateway = env.VITE_GATEWAY_URL || 'http://localhost:8080'
  const authTarget = env.VITE_AUTH_SERVICE_URL || 'http://localhost:8094'

  const serviceProxy = (path, urlKey, fallback) => {
    const useGateway = viaGateway || useAuthCookies
    if (useGateway) {
      return { target: gateway, changeOrigin: true }
    }
    return {
      target: env[urlKey] || fallback,
      changeOrigin: true,
      rewrite: (p) => p.replace(new RegExp(`^${path}`), '')
    }
  }

  return {
    plugins: [vue(), vueDevTools()],
    server: {
      proxy: {
        '/auth-api': {
          target: authTarget,
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/auth-api/, '')
        },
        '/customer-api': serviceProxy('/customer-api', 'VITE_CUSTOMER_SERVICE_URL', 'http://localhost:8083'),
        '/account-api': serviceProxy('/account-api', 'VITE_ACCOUNT_SERVICE_URL', 'http://localhost:8084'),
        '/payment-api': serviceProxy('/payment-api', 'VITE_PAYMENT_SERVICE_URL', 'http://localhost:8086'),
        '/biller-api': serviceProxy('/biller-api', 'VITE_BILLER_SERVICE_URL', 'http://localhost:8088')
      }
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    }
  }
})
