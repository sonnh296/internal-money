import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  server: {
    proxy: {
      // Điểm vào duy nhất qua ApiGateway (port 8080); fallback trực tiếp service nếu gateway tắt
      '/auth-api': {
        target: process.env.VITE_AUTH_SERVICE_URL || 'http://localhost:8094',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/auth-api/, '')
      },
      '/customer-api': {
        target: process.env.VITE_CUSTOMER_SERVICE_URL || 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/customer-api/, '')
      },
      '/account-api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/account-api/, '')
      },
      '/payment-api': {
        target: process.env.VITE_PAYMENT_SERVICE_URL || 'http://localhost:8086',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/payment-api/, '')
      },
      '/biller-api': {
        target: process.env.VITE_BILLER_SERVICE_URL || 'http://localhost:8088',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/biller-api/, '')
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
