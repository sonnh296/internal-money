import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureTokenResolver } from './api/httpClient'
import { setupHttpInterceptors } from './api/httpInterceptors'
import { useAdminAuthStore } from './stores/adminAuth.store'
import { useUserAuthStore } from './stores/userAuth.store'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)

const adminAuth = useAdminAuthStore(pinia)
const userAuth = useUserAuthStore(pinia)

configureTokenResolver(() => {
  const route = router.currentRoute.value
  const portal = (route.meta?.portal as 'admin' | 'user' | undefined) ?? null
  if (portal === 'admin') return { token: adminAuth.accessToken, portal }
  if (portal === 'user') return { token: userAuth.accessToken, portal }
  return { token: '', portal: null }
})

setupHttpInterceptors()

app.mount('#app')
