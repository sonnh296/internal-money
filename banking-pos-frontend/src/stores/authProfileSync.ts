import { meApi } from '../api/auth.api'
import type { AuthProfile, PortalKind } from '../types/api.types'

interface MeResponse {
  email?: string
  customer_id?: string
  scopes?: string[]
  role?: string
}

export async function fetchAuthProfile(portal: PortalKind): Promise<AuthProfile> {
  const { data } = await meApi(portal)
  const me = data as MeResponse
  const scopes = Array.isArray(me.scopes) ? me.scopes.map(String) : []
  if (me.role && !scopes.includes(me.role)) {
    scopes.push(me.role)
  }
  return {
    email: me.email ?? '',
    customerId: me.customer_id ?? '',
    scopes
  }
}
