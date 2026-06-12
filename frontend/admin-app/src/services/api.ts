import axios, { AxiosInstance, AxiosError } from 'axios'
import { AuthResponse, LoginRequest } from '@/types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7000'

class ApiClient {
  private apiInstance: AxiosInstance
  private tokenRefreshPromise: Promise<string> | null = null

  constructor() {
    this.apiInstance = axios.create({
      baseURL: API_BASE_URL,
      timeout: 2000,
      headers: {
        'Content-Type': 'application/json',
      },
    })

    this.setupInterceptors()
  }

  private setupInterceptors(): void {
    // Request interceptor
    this.apiInstance.interceptors.request.use(
      (config) => {
        const token = this.getAccessToken()
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
        return config
      },
      (error) => Promise.reject(error)
    )

    // Response interceptor
    this.apiInstance.interceptors.response.use(
      (response) => response,
      async (error: AxiosError) => {
        const originalRequest = error.config as any

        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true

          try {
            const newToken = await this.refreshToken()
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return this.apiInstance(originalRequest)
          } catch (refreshError) {
            this.clearTokens()
            window.location.href = '/login'
            return Promise.reject(refreshError)
          }
        }

        return Promise.reject(error)
      }
    )
  }

  private getAccessToken(): string | null {
    return localStorage.getItem('access_token')
  }

  private getRefreshToken(): string | null {
    return localStorage.getItem('refresh_token')
  }

  private setTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
  }

  private clearTokens(): void {
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user')
  }

  private async refreshToken(): Promise<string> {
    if (this.tokenRefreshPromise) {
      return this.tokenRefreshPromise
    }

    this.tokenRefreshPromise = (async () => {
      const refreshToken = this.getRefreshToken()
      if (!refreshToken) {
        throw new Error('No refresh token available')
      }

      const response = await this.apiInstance.post<AuthResponse>(
        '/api/v1/auth/refresh',
        { refresh_token: refreshToken }
      )

      const { access_token, refresh_token } = response.data
      this.setTokens(access_token, refresh_token)
      return access_token
    })()

    try {
      return await this.tokenRefreshPromise
    } finally {
      this.tokenRefreshPromise = null
    }
  }

  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await this.apiInstance.post<AuthResponse>(
      '/api/v1/auth/login',
      credentials
    )
    const { access_token, refresh_token, user } = response.data
    this.setTokens(access_token, refresh_token)
    localStorage.setItem('user', JSON.stringify(user))
    return response.data
  }

  async logout(): Promise<void> {
    try {
      await this.apiInstance.post('/api/v1/auth/logout')
    } finally {
      this.clearTokens()
    }
  }

  async validateToken(): Promise<boolean> {
    try {
      await this.apiInstance.get('/api/v1/auth/validate')
      return true
    } catch {
      return false
    }
  }

  get<T = any>(url: string, config?: any) {
    return this.apiInstance.get<T>(url, config)
  }

  post<T = any>(url: string, data?: any, config?: any) {
    return this.apiInstance.post<T>(url, data, config)
  }

  put<T = any>(url: string, data?: any, config?: any) {
    return this.apiInstance.put<T>(url, data, config)
  }

  delete<T = any>(url: string, config?: any) {
    return this.apiInstance.delete<T>(url, config)
  }

  patch<T = any>(url: string, data?: any, config?: any) {
    return this.apiInstance.patch<T>(url, data, config)
  }
}

export const apiClient = new ApiClient()
