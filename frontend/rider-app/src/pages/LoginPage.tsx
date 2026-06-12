import React, { useState } from 'react'
import { Box, Card, TextField, Button, Typography, Alert, CircularProgress, Container, Stack } from '@mui/material'
import { useAuth } from '@/hooks/useAuth'

export const LoginPage: React.FC = () => {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('rider@rideshare.com')
  const [phone, setPhone] = useState('+1987654321')
  const [password, setPassword] = useState('password123')
  const { login, register, isLoading, error } = useAuth()

  const isRegister = mode === 'register'

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (isRegister) {
      if (name && email && phone && password) register({ name, email, phone, password })
      return
    }
    if (phone && password) login({ phone, email, password })
  }

  const handleDemoLogin = () => {
    localStorage.setItem('access_token', 'demo_token_rider_12345')
    localStorage.setItem('refresh_token', 'demo_refresh_token_rider')
    localStorage.setItem('user', JSON.stringify({
      id: 'demo-rider-001',
      name: 'Demo Rider',
      email: 'rider@rideshare.com',
      phone: '+1987654321',
      role: 'rider',
      verified: true,
    }))
    window.location.href = '/'
  }

  return (
    <Container maxWidth="sm">
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', p: 2 }}>
        <Card sx={{
          width: '100%',
          p: { xs: 3, sm: 4 },
          boxShadow: '0 24px 80px rgba(0,0,0,0.35)',
          background: 'linear-gradient(135deg, #1A1F2E 0%, #0F1419 100%)',
          border: '1px solid rgba(0, 255, 136, 0.16)',
          borderRadius: 3,
        }}>
          <Box sx={{ mb: 3, textAlign: 'center' }}>
            <Box sx={{
              width: 72,
              height: 72,
              background: 'linear-gradient(135deg, #00FF88, #00D9FF)',
              borderRadius: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
              boxShadow: '0 0 30px rgba(0, 255, 136, 0.22)'
            }}>
              <Typography sx={{ fontSize: 32, fontWeight: 800, color: '#0F1419', fontFamily: "'Space Mono', monospace" }}>
                RS
              </Typography>
            </Box>
            <Typography variant="h4" sx={{ fontWeight: 800, mb: 1, color: '#fff', fontFamily: "'Space Mono', monospace" }}>
              RideShare<span style={{ background: 'linear-gradient(135deg, #00FF88, #00D9FF)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>Rider</span>
            </Typography>
            <Typography sx={{ color: '#8B92A6' }}>{isRegister ? 'Create your rider account.' : 'Your journey, your way.'}</Typography>
          </Box>

          <Stack direction="row" spacing={1} sx={{ mb: 3 }}>
            <Button fullWidth variant={mode === 'login' ? 'contained' : 'outlined'} onClick={() => setMode('login')}>
              Login
            </Button>
            <Button fullWidth variant={mode === 'register' ? 'contained' : 'outlined'} onClick={() => setMode('register')}>
              Create Account
            </Button>
          </Stack>

          {error && <Alert severity="error" sx={{ mb: 2 }}>{error instanceof Error ? error.message : 'Authentication failed'}</Alert>}

          <form onSubmit={handleSubmit}>
            {isRegister && (
              <TextField
                fullWidth
                label="Full Name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                margin="normal"
                required
                disabled={isLoading}
              />
            )}
            <TextField
              fullWidth
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              margin="normal"
              required={isRegister}
              disabled={isLoading}
            />
            <TextField
              fullWidth
              label="Phone Number"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              margin="normal"
              required
              disabled={isLoading}
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
            />

            <Button
              fullWidth
              variant="contained"
              type="submit"
              sx={{
                mt: 3,
                py: 1.4,
                background: 'linear-gradient(135deg, #00FF88, #00D9FF)',
                color: '#0F1419',
                fontWeight: 800,
                textTransform: 'uppercase',
                '&:hover': { boxShadow: '0 0 30px rgba(0, 255, 136, 0.3)', transform: 'translateY(-2px)' },
              }}
              disabled={isLoading || !phone || !password || (isRegister && (!name || !email))}
            >
              {isLoading ? <CircularProgress size={20} sx={{ color: 'inherit' }} /> : isRegister ? 'Create Rider Account' : 'Sign In'}
            </Button>

            <Button
              fullWidth
              variant="outlined"
              onClick={handleDemoLogin}
              disabled={isLoading}
              sx={{ mt: 2, color: '#00D9FF', borderColor: '#00D9FF', fontWeight: 700 }}
            >
              Demo Login
            </Button>
          </form>
        </Card>
      </Box>
    </Container>
  )
}
