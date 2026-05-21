import axios, { type AxiosInstance } from 'axios'
import { env } from '../config/env'
import type { PortalKind, ServiceName } from '../types/api.types'

const axiosDefaults = env.useAuthCookies ? { withCredentials: true } : {}

const clients: Record<ServiceName, AxiosInstance> = {
  auth: axios.create({ baseURL: env.authBaseUrl, ...axiosDefaults }),
  customer: axios.create({ baseURL: env.customerBaseUrl, ...axiosDefaults }),
  account: axios.create({ baseURL: env.accountBaseUrl, ...axiosDefaults }),
  payment: axios.create({ baseURL: env.paymentBaseUrl, ...axiosDefaults }),
  biller: axios.create({ baseURL: env.billerBaseUrl, ...axiosDefaults })
}

type TokenResolver = () => { token: string; portal: PortalKind | null }

let resolver: TokenResolver = () => ({ token: '', portal: null })

export function configureTokenResolver(fn: TokenResolver): void {
  resolver = fn
}

for (const client of Object.values(clients)) {
  client.interceptors.request.use((config) => {
    const { token, portal } = resolver()
    if (token && !env.useAuthCookies) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const explicitPortal = config.headers['X-Portal']
    if (!explicitPortal && portal) {
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
