const required = (name: string, fallback: string): string => {
  return (import.meta.env[name] as string | undefined) ?? fallback
}

export const env = {
  authBaseUrl: required('VITE_AUTH_URL', '/auth-api'),
  customerBaseUrl: required('VITE_CUSTOMER_URL', '/customer-api'),
  accountBaseUrl: required('VITE_ACCOUNT_URL', '/account-api'),
  paymentBaseUrl: required('VITE_PAYMENT_URL', '/payment-api'),
  billerBaseUrl: required('VITE_BILLER_URL', '/biller-api')
}
