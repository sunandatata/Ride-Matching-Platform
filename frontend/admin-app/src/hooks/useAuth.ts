import { useQuery, useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authService } from '@/services/authService'
import { useAuthStore } from '@/store/authStore'
import { LoginRequest } from '@/types'

export const useAuth = () => {
  const navigate = useNavigate()
  const { user, setUser, setAuthenticated, logout: storeLogout } = useAuthStore()

  const loginMutation = useMutation({
    mutationFn: (credentials: LoginRequest) => authService.login(credentials),
    onSuccess: (data) => {
      setUser(data.user)
      setAuthenticated(true)
      navigate('/dashboard')
    },
  })

  const logoutMutation = useMutation({
    mutationFn: () => authService.logout(),
    onSuccess: () => {
      storeLogout()
      navigate('/login')
    },
  })

  const validateTokenQuery = useQuery({
    queryKey: ['auth', 'validate'],
    queryFn: () => authService.validateToken(),
    retry: 1,
    enabled: !!localStorage.getItem('access_token'),
  })

  return {
    user,
    isAuthenticated: !!user && authService.isAuthenticated(),
    login: loginMutation.mutate,
    logout: logoutMutation.mutate,
    isLoading: loginMutation.isPending || logoutMutation.isPending,
    isValidating: validateTokenQuery.isFetching,
    error: loginMutation.error || logoutMutation.error,
  }
}
