import { create } from 'zustand'
import { User } from '@/types'

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  setUser: (user: User | null) => void
  setAuthenticated: (isAuthenticated: boolean) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: (() => { const stored = localStorage.getItem('user'); return stored ? JSON.parse(stored) : null })(),
  isAuthenticated: !!localStorage.getItem('access_token'),
  setUser: (user) => set({ user }),
  setAuthenticated: (isAuthenticated) => set({ isAuthenticated }),
  logout: () => { localStorage.removeItem('access_token'); localStorage.removeItem('refresh_token'); localStorage.removeItem('user'); set({ user: null, isAuthenticated: false }) },
}))
