import React, { useState } from 'react'
import {
  Box,
  AppBar,
  Toolbar,
  Drawer,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  IconButton,
  Typography,
  Avatar,
  Menu,
  MenuItem,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import {
  Dashboard as DashboardIcon,
  DirectionsCar as DriversIcon,
  LocalTaxi as RidesIcon,
  BarChart as AnalyticsIcon,
  Settings as SettingsIcon,
  Menu as MenuIcon,
  Logout as LogoutIcon,
} from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { useLocation } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

interface LayoutProps {
  children: React.ReactNode
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuth()
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))

  const menuItems = [
    { label: 'Dashboard', icon: DashboardIcon, path: '/dashboard' },
    { label: 'Drivers', icon: DriversIcon, path: '/drivers' },
    { label: 'Rides', icon: RidesIcon, path: '/rides' },
    { label: 'Analytics', icon: AnalyticsIcon, path: '/analytics' },
    { label: 'Settings', icon: SettingsIcon, path: '/settings' },
  ]

  const handleProfileMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget)
  }

  const handleProfileMenuClose = () => {
    setAnchorEl(null)
  }

  const handleLogout = () => {
    handleProfileMenuClose()
    logout()
  }

  const drawerWidth = 240

  const drawer = (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
      }}
    >
      <Box sx={{ p: 2.5 }}>
        <Typography variant="h6" sx={{ fontWeight: 900, color: '#FFFFFF', letterSpacing: 0 }}>
          RideOps
        </Typography>
        <Typography sx={{ color: '#94A3B8', fontSize: 12, fontWeight: 700 }}>
          Control center
        </Typography>
      </Box>
      <Divider sx={{ borderColor: 'rgba(255,255,255,0.08)' }} />
      <List sx={{ flex: 1, p: 1.5 }}>
        {menuItems.map((item) => {
          const Icon = item.icon
          const active = location.pathname === item.path
          return (
            <ListItemButton
              key={item.path}
              onClick={() => {
                navigate(item.path)
                setDrawerOpen(false)
              }}
              sx={{
                mb: 0.5,
                borderRadius: 2,
                color: active ? '#FFFFFF' : '#CBD5E1',
                bgcolor: active ? 'rgba(37, 99, 235, 0.22)' : 'transparent',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.08)' },
              }}
            >
              <ListItemIcon sx={{ color: active ? '#93C5FD' : '#94A3B8', minWidth: 38 }}>
                <Icon />
              </ListItemIcon>
              <ListItemText
                primary={item.label}
                primaryTypographyProps={{ fontWeight: active ? 900 : 700, fontSize: 14 }}
              />
            </ListItemButton>
          )
        })}
      </List>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', height: '100vh', bgcolor: '#F3F6FA' }}>
      <AppBar
        position="fixed"
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
          backgroundColor: '#FFFFFF',
          color: '#0F172A',
          boxShadow: '0 1px 0 #E2E8F0',
        }}
      >
        <Toolbar sx={{ minHeight: '64px !important' }}>
          {isMobile && (
            <IconButton
              color="inherit"
              edge="start"
              onClick={() => setDrawerOpen(true)}
              sx={{ mr: 2 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" sx={{ flex: 1, fontWeight: 900, letterSpacing: 0 }}>
            Admin Dashboard
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
              <Typography sx={{ color: '#0F172A', fontWeight: 900, fontSize: 14 }}>{user?.name}</Typography>
              <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 700 }}>Administrator</Typography>
            </Box>
            <Avatar
              onClick={handleProfileMenuOpen}
              sx={{
                width: 40,
                height: 40,
                cursor: 'pointer',
                backgroundColor: '#2563EB',
                fontWeight: 900,
              }}
            >
              {user?.name?.charAt(0).toUpperCase()}
            </Avatar>
            <Menu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={handleProfileMenuClose}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            >
              <MenuItem disabled>
                <Typography variant="body2" sx={{ fontWeight: 800 }}>{user?.name}</Typography>
              </MenuItem>
              <MenuItem onClick={handleLogout}>
                <LogoutIcon sx={{ mr: 1, fontSize: 20 }} />
                Logout
              </MenuItem>
            </Menu>
          </Box>
        </Toolbar>
      </AppBar>

      {!isMobile && (
        <Drawer
          variant="permanent"
          sx={{
            width: drawerWidth,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: drawerWidth,
              boxSizing: 'border-box',
              marginTop: '64px',
              height: 'calc(100vh - 64px)',
              borderRight: 'none',
              background: '#0F172A',
            },
          }}
        >
          {drawer}
        </Drawer>
      )}

      {isMobile && (
        <Drawer
          anchor="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          sx={{
            '& .MuiDrawer-paper': {
              width: drawerWidth,
              boxSizing: 'border-box',
              marginTop: '64px',
              background: '#0F172A',
            },
          }}
        >
          {drawer}
        </Drawer>
      )}

      <Box
        sx={{
          flex: 1,
          overflow: 'auto',
          marginTop: '64px',
          backgroundColor: '#F3F6FA',
        }}
      >
        {children}
      </Box>
    </Box>
  )
}
