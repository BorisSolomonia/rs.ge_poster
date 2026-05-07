import axios from 'axios'
import { env } from '../env'

export class ApiClientError extends Error {
  status?: number
  code?: string
  timestamp?: string
  details?: unknown

  constructor(message: string, status?: number, code?: string, timestamp?: string, details?: unknown) {
    super(message)
    this.name = 'ApiClientError'
    this.status = status
    this.code = code
    this.timestamp = timestamp
    this.details = details
  }
}

export const client = axios.create({
  baseURL: env.apiBaseUrl,
})

client.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status
    const code = error.response?.data?.code
    const timestamp = error.response?.data?.timestamp
    const apiError = error.response?.data?.error
    const apiMessage = error.response?.data?.message
    const msg = apiError || apiMessage || error.message || 'Unknown error'
    return Promise.reject(new ApiClientError(status ? `${status}: ${msg}` : msg, status, code, timestamp, error.response?.data))
  }
)

export function unwrapData<T>(response: { data: { data: T; success?: boolean; error?: string | null; code?: string | null; timestamp?: string } }): T {
  if (response.data.success === false) {
    throw new ApiClientError(
      response.data.error || 'API request failed',
      undefined,
      response.data.code || undefined,
      response.data.timestamp,
      response.data,
    )
  }
  return response.data.data
}
