import React, { useState } from 'react'
import {
  Box,
  Card,
  TextField,
  Button,
  Typography,
  Alert,
  CircularProgress,
  Container,
} from '@mui/material'
import { useAuth } from '@/hooks/useAuth'

export const LoginPage: React.FC = () => {
  const [phone, setPhone] = useState('+1234567890')
  const [password, setPassword] = useState('admin123')
  const { login, isLoading, error } = useAuth()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (phone && password) {
      login({ phone, password })
    }
  }

  const handleDemoLogin = () => {
    localStorage.setItem('access_token', 'demo_token_admin_12345')
    localStorage.setItem('refresh_token', 'demo_refresh_token_admin')
    localStorage.setItem('user', JSON.stringify({
      id: 'demo-admin-001',
      name: 'Demo Admin',
      email: 'admin@rideshare.com',
      phone: '+1987654321',
      role: 'admin'
    }))
    window.location.href = '/'
  }

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
        }}
      >
        <Card
          sx={{
            width: '100%',
            p: 4,
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            background: 'linear-gradient(135deg, #1A1F2E 0%, #0F1419 100%)',
            border: '1px solid rgba(0, 255, 136, 0.1)'
          }}
        >
          <Box sx={{ mb: 3, textAlign: 'center' }}>
            <Box sx={{
              width: 80,
              height: 80,
              background: 'linear-gradient(135deg, #00FF88, #00D9FF)',
              borderRadius: '12px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
              transform: 'rotate(-15deg)',
              boxShadow: '0 0 24px rgba(0, 255, 136, 0.2)'
            }}>
              <Typography sx={{
                fontSize: 36,
                fontWeight: 700,
                color: '#0F1419',
                transform: 'rotate(15deg)',
                fontFamily: "'Space Mono', monospace"
              }}>
                RS
              </Typography>
            </Box>
            <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1, color: '#fff', fontFamily: "'Space Mono', monospace" }}>
              RideShare<span style={{background: 'linear-gradient(135deg, #00FF88, #00D9FF)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text'}}>Admin</span>
            </Typography>
            <Typography color="textSecondary" sx={{ color: '#8B92A6' }}>Manage the network. Monitor operations.</Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error instanceof Error ? error.message : 'Login failed'}
            </Alert>
          )}

          <form onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label="Phone Number"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              margin="normal"
              required
              disabled={isLoading}
              sx={{
                '& .MuiOutlinedInput-root': {
                  color: '#fff',
                  backgroundColor: 'rgba(255, 255, 255, 0.04)',
                  '& fieldset': { borderColor: 'rgba(0, 255, 136, 0.2)' },
                  '&:hover fieldset': { borderColor: 'rgba(0, 255, 136, 0.4)' },
                  '&.Mui-focused fieldset': { borderColor: '#00FF88', boxShadow: '0 0 20px rgba(0, 255, 136, 0.3)' }
                },
                '& .MuiInputBase-input::placeholder': { color: '#8B92A6', opacity: 0.7 }
              }}
            />
            <TextField
              fullWidth
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              margin="normal"
              required
              disabled={isLoading}
              sx={{
                '& .MuiOutlinedInput-root': {
                  color: '#fff',
                  backgroundColor: 'rgba(255, 255, 255, 0.04)',
                  '& fieldset': { borderColor: 'rgba(0, 255, 136, 0.2)' },
                  '&:hover fieldset': { borderColor: 'rgba(0, 255, 136, 0.4)' },
                  '&.Mui-focused fieldset': { borderColor: '#00FF88', boxShadow: '0 0 20px rgba(0, 255, 136, 0.3)' }
                }
              }}
            />
            <Button
              fullWidth
              variant="contained"
              type="submit"
              sx={{
                mt: 3,
                background: 'linear-gradient(135deg, #00FF88, #00D9FF)',
                color: '#0F1419',
                fontWeight: 600,
                textTransform: 'uppercase',
                '&:hover': {
                  boxShadow: '0 0 30px rgba(0, 255, 136, 0.3)',
                  transform: 'translateY(-2px)'
                },
                '&:disabled': { opacity: 0.6 }
              }}
              disabled={isLoading || !phone || !password}
            >
              {isLoading ? <CircularProgress size={20} sx={{ color: 'inherit' }} /> : 'Sign In'}
            </Button>

            <Button
              fullWidth
              variant="outlined"
              onClick={handleDemoLogin}
              disabled={isLoading}
              sx={{
                mt: 2,
                color: '#00D9FF',
                borderColor: '#00D9FF',
                textTransform: 'uppercase',
                fontWeight: 600,
                '&:hover': {
                  background: 'rgba(0, 217, 255, 0.1)',
                  boxShadow: '0 0 20px rgba(0, 217, 255, 0.2)',
                  transform: 'translateY(-2px)'
                }
              }}
            >
              Demo Login
            </Button>
          </form>

          <Box sx={{ mt: 2, textAlign: 'center', borderTop: '1px solid rgba(0, 255, 136, 0.1)', pt: 2 }}>
            <Typography variant="body2" sx={{ color: '#8B92A6' }}>
              Admin portal access only
            </Typography>
          </Box>
        </Card>
      </Box>
    </Container>
  )
}
