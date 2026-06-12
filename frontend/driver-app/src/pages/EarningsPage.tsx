import React, { useState } from 'react'
import {
  Box,
  Typography,
  ToggleButton,
  ToggleButtonGroup,
  CircularProgress,
} from '@mui/material'
import { TrendingUp as TrendingIcon, LocalTaxi as RideIcon, AttachMoney as MoneyIcon, Star as StarIcon } from '@mui/icons-material'
import { useRideHistory, useDriverEarnings } from '@/hooks/useRides'
import dayjs from 'dayjs'
import '../styles/EarningsPage.css'

export const EarningsPage: React.FC = () => {
  const [period, setPeriod] = useState<'day' | 'week' | 'month'>('day')
  const { data: rideHistory, isLoading } = useRideHistory()
  const { data: earnings } = useDriverEarnings()

  const handlePeriodChange = (
    _event: React.MouseEvent<HTMLElement>,
    newPeriod: 'day' | 'week' | 'month' | null
  ) => {
    if (newPeriod) setPeriod(newPeriod)
  }

  const getPeriodEarnings = () => {
    if (!earnings) return 0
    switch (period) {
      case 'day':
        return earnings.daily || 0
      case 'week':
        return earnings.weekly || 0
      default:
        return earnings.total || 0
    }
  }

  const avgRating = rideHistory?.data?.length
    ? (rideHistory.data.reduce((sum, ride) => sum + (ride.rating || 0), 0) / rideHistory.data.length).toFixed(2)
    : '0'

  const statCards = [
    {
      label: period === 'day' ? 'Today\'s Earnings' : period === 'week' ? 'This Week\'s Earnings' : 'Total Earnings',
      value: `$${getPeriodEarnings().toFixed(2)}`,
      icon: <MoneyIcon />,
      gradient: 'electric-cyan',
    },
    {
      label: 'Total Completed Rides',
      value: earnings?.rideCount || 0,
      icon: <RideIcon />,
      gradient: 'cyan-magenta',
    },
    {
      label: 'Average Rating',
      value: `${avgRating}/5`,
      icon: <StarIcon />,
      gradient: 'magenta-gold',
    },
    {
      label: 'Acceptance Rate',
      value: '98%',
      icon: <TrendingIcon />,
      gradient: 'gold-electric',
    },
  ]

  return (
    <Box className="earnings-page">
      {/* Header */}
      <Box className="earnings-header">
        <Typography variant="h4" className="header-title">
          Earnings & Stats
        </Typography>
        <Typography className="header-subtitle">
          Track your performance and earnings
        </Typography>
      </Box>

      {/* Period toggle */}
      <Box className="period-toggle-container">
        <ToggleButtonGroup
          value={period}
          exclusive
          onChange={handlePeriodChange}
          size="small"
          className="period-toggle"
        >
          <ToggleButton value="day">Today</ToggleButton>
          <ToggleButton value="week">This Week</ToggleButton>
          <ToggleButton value="month">This Month</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {/* Stats grid */}
      <Box className="stats-grid">
        {statCards.map((card, index) => (
          <Box
            key={card.label}
            className={`stat-card ${card.gradient}`}
            style={{ animationDelay: `${index * 50}ms` }}
          >
            <Box className="stat-icon">{card.icon}</Box>
            <Box className="stat-content">
              <Typography className="stat-label">{card.label}</Typography>
              <Typography className="stat-value">{card.value}</Typography>
            </Box>
          </Box>
        ))}
      </Box>

      {/* Recent rides section */}
      <Box className="recent-rides-section">
        <Typography variant="h5" className="section-title">Recent Rides</Typography>

        {isLoading ? (
          <Box className="loading-state">
            <CircularProgress sx={{ color: 'var(--color-accent-electric)' }} />
            <Typography className="loading-text">Loading ride history...</Typography>
          </Box>
        ) : rideHistory?.data && rideHistory.data.length > 0 ? (
          <Box className="rides-list">
            {rideHistory.data.slice(0, 8).map((ride, index) => (
              <Box
                key={ride.id}
                className="ride-item"
                style={{ animationDelay: `${index * 30}ms` }}
              >
                <Box className="ride-info">
                  <Box>
                    <Typography className="ride-date">
                      {dayjs(ride.created_at).format('MMM DD, HH:mm')}
                    </Typography>
                    <Typography className="ride-distance">
                      {ride.distance.toFixed(2)} km
                    </Typography>
                    <Typography className={`ride-status status-${ride.status}`}>
                      {ride.status.replace('_', ' ')}
                    </Typography>
                  </Box>
                </Box>
                <Box className="ride-divider" />
                <Box className="ride-fare">
                  ${(ride.actual_fare || ride.estimated_fare).toFixed(2)}
                </Box>
              </Box>
            ))}
          </Box>
        ) : (
          <Box className="empty-state">
            <Box className="empty-icon">📊</Box>
            <Typography className="empty-title">No ride history yet</Typography>
            <Typography className="empty-text">
              Your completed rides will appear here
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  )
}
