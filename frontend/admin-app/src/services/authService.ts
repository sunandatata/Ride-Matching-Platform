import { apiClient } from './api'
import { AuthResponse, LoginRequest, User } from '@/types'

export const authService = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    return apiClient.login({ ...credentials, role: 'admin' })
  },

  logout: async (): Promise<void> => {
    return apiClient.logout()
  },

  validateToken: async (): Promise<boolean> => {
    return apiClient.validateToken()
  },

  getCurrentUser: (): User | null => {
    const user = localStorage.getItem('user')
    return user ? JSON.parse(user) : null
  },

  isAuthenticated: (): boolean => {
    return !!localStorage.getItem('access_token')
  },
}
