import { apiClient } from './api'
import { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types'

export const authService = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => apiClient.login({ ...credentials, role: 'rider' }),
  register: async (data: RegisterRequest): Promise<AuthResponse> => apiClient.register({ ...data, role: 'rider' }),
  logout: async (): Promise<void> => apiClient.logout(),
  validateToken: async (): Promise<boolean> => apiClient.validateToken(),
  getCurrentUser: (): User | null => {
    try {
      const user = localStorage.getItem('user')
      return user ? JSON.parse(user) : null
    } catch {
      localStorage.removeItem('user')
      return null
    }
  },
  isAuthenticated: (): boolean => !!localStorage.getItem('access_token'),
}
