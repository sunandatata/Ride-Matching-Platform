import React from 'react'
import { BottomNavigation, BottomNavigationAction } from '@mui/material'
import { Home as HomeIcon, LocalTaxi as RideIcon, Person as ProfileIcon } from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'

export const BottomNav: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()

  const getValue = () => {
    switch (location.pathname) {
      case '/home': return 'home'
      case '/rides': return 'rides'
      case '/profile': return 'profile'
      default: return 'home'
    }
  }

  return (
    <BottomNavigation value={getValue()} onChange={(_, value) => navigate(`/${value}`)}>
      <BottomNavigationAction label="Home" value="home" icon={<HomeIcon />} />
      <BottomNavigationAction label="Rides" value="rides" icon={<RideIcon />} />
      <BottomNavigationAction label="Profile" value="profile" icon={<ProfileIcon />} />
    </BottomNavigation>
  )
}
