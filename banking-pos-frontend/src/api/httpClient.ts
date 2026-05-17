import axios, { type AxiosInstance } from 'axios'
import { env } from '../config/env'
import type { PortalKind, ServiceName } from '../types/api.types'

const clients: Record<ServiceName, AxiosInstance> = {
  auth: axios.create({ baseURL: env.authBaseUrl }),
  customer: axios.create({ baseURL: env.customerBaseUrl }),
  account: axios.create({ baseURL: env.accountBaseUrl }),
  payment: axios.create({ baseURL: env.paymentBaseUrl }),
  biller: axios.create({ baseURL: env.billerBaseUrl })
}

type TokenResolver = () => { token: string; portal: PortalKind | null }

let resolver: TokenResolver = () => ({ token: '', portal: null })

export function configureTokenResolver(fn: TokenResolver): void {
  resolver = fn
}

for (const client of Object.values(clients)) {
  client.interceptors.request.use((config) => {
    const { token, portal } = resolver()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (portal) {
      config.headers['X-Portal'] = portal
    }
    return config
  })
}

export function getClient(service: ServiceName): AxiosInstance {
  return clients[service]
}

export function rawClient(baseURL: string): AxiosInstance {
  return axios.create({ baseURL })
}
