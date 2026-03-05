import axios from 'axios'
import { env } from '../env'

export const client = axios.create({
  baseURL: env.apiBaseUrl,
})

client.interceptors.response.use(
  (res) => res,
  (error) => {
    const msg = error.response?.data?.error || error.message || 'Unknown error'
    return Promise.reject(new Error(msg))
  }
)
