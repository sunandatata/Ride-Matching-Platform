import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { ThemeProvider, createTheme } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { Box } from '@mui/material'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { BottomNav } from '@/components/BottomNav'
import { LoginPage } from '@/pages/LoginPage'
import { HomePage } from '@/pages/HomePage'
import { RideHistoryPage } from '@/pages/RideHistoryPage'
import { ProfilePage } from '@/pages/ProfilePage'
import { useAuth } from '@/hooks/useAuth'

const theme = createTheme({
  palette: {
    primary: { main: '#1976d2' },
    secondary: { main: '#dc004e' },
    background: { default: '#fafafa' },
  },
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "Roboto", sans-serif',
  },
})

const AppRoutes: React.FC = () => {
  const { isAuthenticated, isValidating } = useAuth()

  if (isValidating) return null

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/home" element={<ProtectedRoute><Box sx={{ pb: 7 }}><HomePage /></Box><BottomNav /></ProtectedRoute>} />
      <Route path="/rides" element={<ProtectedRoute><RideHistoryPage /><BottomNav /></ProtectedRoute>} />
      <Route path="/profile" element={<ProtectedRoute><ProfilePage /><BottomNav /></ProtectedRoute>} />
      <Route path="/" element={<Navigate to={isAuthenticated ? '/home' : '/login'} replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

const App: React.FC = () => {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Router>
        <AppRoutes />
      </Router>
    </ThemeProvider>
  )
}

export default App
