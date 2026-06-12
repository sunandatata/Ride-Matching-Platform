import React from 'react'
import { Box } from '@mui/material'
import { Home as HomeIcon, LocalTaxi as RideIcon, TrendingUp as EarningsIcon, Person as ProfileIcon } from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'
import '../styles/BottomNav.css'

interface NavItem {
  value: string
  icon: React.ReactNode
  label: string
}

export const BottomNav: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()

  const navItems: NavItem[] = [
    { value: 'home', icon: <HomeIcon />, label: 'Home' },
    { value: 'rides', icon: <RideIcon />, label: 'Rides' },
    { value: 'earnings', icon: <EarningsIcon />, label: 'Earnings' },
    { value: 'profile', icon: <ProfileIcon />, label: 'Profile' },
  ]

  const getCurrentValue = () => {
    const path = location.pathname
    if (path === '/home') return 'home'
    if (path === '/rides') return 'rides'
    if (path === '/earnings') return 'earnings'
    if (path === '/profile') return 'profile'
    return 'home'
  }

  const current = getCurrentValue()

  return (
    <Box className="bottom-nav">
      {navItems.map((item) => (
        <Box
          key={item.value}
          className={`nav-item ${current === item.value ? 'active' : ''}`}
          onClick={() => navigate(`/${item.value}`)}
        >
          <Box className="nav-icon">{item.icon}</Box>
          <Box className="nav-label">{item.label}</Box>
        </Box>
      ))}
    </Box>
  )
}
