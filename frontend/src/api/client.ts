import axios from 'axios'
import { env } from '../env'

export class ApiClientError extends Error {
  status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'ApiClientError'
    this.status = status
  }
}

export const client = axios.create({
  baseURL: env.apiBaseUrl,
})

client.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status
    const apiError = error.response?.data?.error
    const apiMessage = error.response?.data?.message
    const msg = apiError || apiMessage || error.message || 'Unknown error'
    return Promise.reject(new ApiClientError(status ? `${status}: ${msg}` : msg, status))
  }
)
