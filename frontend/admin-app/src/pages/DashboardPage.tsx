import React, { useState } from 'react'
import {
  Alert,
  Box,
  Grid,
  Typography,
  ToggleButton,
  ToggleButtonGroup,
  Card,
  CardContent,
  CircularProgress,
  Chip,
  Stack,
} from '@mui/material'
import {
  AttachMoney as RevenueIcon,
  CheckCircle as HealthIcon,
  DirectionsCar as DriversIcon,
  LocalTaxi as RidesIcon,
  People as RidersIcon,
  PendingActions as ActiveIcon,
  Timeline as AnalyticsIcon,
} from '@mui/icons-material'
import { MetricCard } from '@/components/MetricCard'
import {
  useDriverStats,
  usePlatformMetrics,
  useRevenueChart,
  useRideChart,
  useRideStats,
} from '@/hooks/useDashboard'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

const chartCardSx = {
  borderRadius: 2,
  boxShadow: '0 12px 30px rgba(15, 23, 42, 0.08)',
  border: '1px solid #E2E8F0',
  height: '100%',
}

export const DashboardPage: React.FC = () => {
  const [period, setPeriod] = useState<'day' | 'week' | 'month'>('month')
  const { data: metrics, isLoading: metricsLoading, error: metricsError } = usePlatformMetrics()
  const { data: rideStats, isLoading: rideStatsLoading } = useRideStats()
  const { data: driverStats, isLoading: driverStatsLoading } = useDriverStats()
  const { data: chartData, isLoading: chartLoading } = useRevenueChart(period)
  const { data: rideChartData, isLoading: rideChartLoading } = useRideChart(period)

  const handlePeriodChange = (
    _event: React.MouseEvent<HTMLElement>,
    newPeriod: 'day' | 'week' | 'month' | null
  ) => {
    if (newPeriod) setPeriod(newPeriod)
  }

  const totalRides = rideStats?.total_rides || 0
  const completedRides = rideStats?.completed_rides || 0
  const cancelledRides = rideStats?.cancelled_rides || 0
  const activeRides = Math.max(0, totalRides - completedRides - cancelledRides)
  const onlineDrivers = metrics?.active_drivers || 0
  const availableDrivers = driverStats?.active_drivers || onlineDrivers
  const recentActivity = Array.isArray(chartData) ? chartData.slice(-5).reverse() : []

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
            Operations Dashboard
          </Typography>
          <Typography sx={{ color: '#64748B', mt: 0.5 }}>
            Live platform metrics, ride analytics, and operational activity.
          </Typography>
        </Box>
        <ToggleButtonGroup
          value={period}
          exclusive
          onChange={handlePeriodChange}
          size="small"
          sx={{
            bgcolor: '#FFFFFF',
            border: '1px solid #E2E8F0',
            borderRadius: 2,
            '& .MuiToggleButton-root': { px: 2, fontWeight: 800, border: 0 },
          }}
        >
          <ToggleButton value="day">Day</ToggleButton>
          <ToggleButton value="week">Week</ToggleButton>
          <ToggleButton value="month">Month</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {metricsError && (
        <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>
          Failed to load dashboard metrics
        </Alert>
      )}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Total Rides" value={totalRides} icon={<RidesIcon />} isLoading={rideStatsLoading} tone="blue" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Active Rides" value={activeRides} icon={<ActiveIcon />} isLoading={rideStatsLoading} tone="amber" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Available Drivers" value={availableDrivers} icon={<DriversIcon />} isLoading={driverStatsLoading || metricsLoading} tone="green" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Online Drivers" value={onlineDrivers} icon={<DriversIcon />} isLoading={metricsLoading} tone="violet" />
        </Grid>
      </Grid>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Active Riders" value={metrics?.active_riders || 0} icon={<RidersIcon />} isLoading={metricsLoading} tone="blue" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Today Rides" value={metrics?.total_rides_today || 0} icon={<RidesIcon />} isLoading={metricsLoading} tone="green" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Today Revenue" value={`$${metrics?.total_revenue_today?.toFixed(2) || '0.00'}`} icon={<RevenueIcon />} isLoading={metricsLoading} tone="amber" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Total Revenue" value={`$${rideStats?.total_revenue?.toFixed(2) || '0.00'}`} icon={<RevenueIcon />} isLoading={rideStatsLoading} tone="violet" />
        </Grid>
      </Grid>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} lg={8}>
          <Card sx={chartCardSx}>
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 900, color: '#0F172A' }}>
                    Revenue Trend
                  </Typography>
                  <Typography sx={{ color: '#64748B', fontSize: 13 }}>Gross platform revenue by selected period</Typography>
                </Box>
                <AnalyticsIcon sx={{ color: '#2563EB' }} />
              </Box>

              {chartLoading ? (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={320}>
                  <CircularProgress />
                </Box>
              ) : chartData?.length ? (
                <ResponsiveContainer width="100%" height={320}>
                  <AreaChart data={chartData}>
                    <defs>
                      <linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#2563EB" stopOpacity={0.32} />
                        <stop offset="95%" stopColor="#2563EB" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                    <XAxis dataKey="date" tick={{ fill: '#64748B', fontSize: 12 }} />
                    <YAxis tick={{ fill: '#64748B', fontSize: 12 }} />
                    <Tooltip />
                    <Area type="monotone" dataKey="revenue" stroke="#2563EB" strokeWidth={3} fill="url(#revenueFill)" />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={320}>
                  <Typography sx={{ color: '#64748B' }}>No revenue data available</Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={4}>
          <Card sx={chartCardSx}>
            <CardContent sx={{ p: 2.5 }}>
              <Typography variant="h6" sx={{ fontWeight: 900, color: '#0F172A', mb: 2 }}>
                Ride Analytics
              </Typography>
              <Stack spacing={1.5}>
                {[
                  ['Completed rides', completedRides, '#16A34A'],
                  ['Cancelled rides', cancelledRides, '#DC2626'],
                  ['Average rating', (rideStats?.average_rating || metrics?.average_ride_rating || 0).toFixed(2), '#7C3AED'],
                  ['Pending approvals', metrics?.pending_approvals || driverStats?.pending_approvals || 0, '#D97706'],
                ].map(([label, value, color]) => (
                  <Box key={String(label)} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 1.5, borderRadius: 2, bgcolor: '#F8FAFC' }}>
                    <Typography sx={{ color: '#475569', fontWeight: 800 }}>{label}</Typography>
                    <Typography sx={{ color: String(color), fontWeight: 900 }}>{value}</Typography>
                  </Box>
                ))}
              </Stack>

              <Box sx={{ mt: 2.5, height: 160 }}>
                {rideChartLoading ? (
                  <Box display="flex" justifyContent="center" alignItems="center" height="100%">
                    <CircularProgress size={24} />
                  </Box>
                ) : rideChartData?.length ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={rideChartData}>
                      <XAxis dataKey="date" hide />
                      <YAxis hide />
                      <Tooltip />
                      <Bar dataKey="rides" fill="#16A34A" radius={[6, 6, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                ) : null}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={7}>
          <Card sx={chartCardSx}>
            <CardContent sx={{ p: 2.5 }}>
              <Typography variant="h6" sx={{ fontWeight: 900, color: '#0F172A', mb: 2 }}>
                Ride Volume
              </Typography>
              {rideChartLoading ? (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={240}>
                  <CircularProgress />
                </Box>
              ) : rideChartData?.length ? (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={rideChartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                    <XAxis dataKey="date" tick={{ fill: '#64748B', fontSize: 12 }} />
                    <YAxis tick={{ fill: '#64748B', fontSize: 12 }} />
                    <Tooltip />
                    <Line type="monotone" dataKey="rides" stroke="#16A34A" strokeWidth={3} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={240}>
                  <Typography sx={{ color: '#64748B' }}>No ride volume data available</Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card sx={chartCardSx}>
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant="h6" sx={{ fontWeight: 900, color: '#0F172A' }}>
                  Recent Activity
                </Typography>
                <Chip
                  icon={<HealthIcon />}
                  label={metrics?.system_health?.api_status || 'healthy'}
                  color={metrics?.system_health?.api_status === 'down' ? 'error' : 'success'}
                  size="small"
                  sx={{ fontWeight: 800 }}
                />
              </Box>

              <Stack spacing={1.25}>
                {recentActivity.length ? recentActivity.map((entry: any) => (
                  <Box key={entry.date} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, p: 1.5, borderRadius: 2, bgcolor: '#F8FAFC' }}>
                    <Box>
                      <Typography sx={{ color: '#0F172A', fontWeight: 900 }}>{entry.date}</Typography>
                      <Typography sx={{ color: '#64748B', fontSize: 13 }}>Revenue update</Typography>
                    </Box>
                    <Typography sx={{ color: '#16A34A', fontWeight: 900 }}>${Number(entry.revenue || 0).toFixed(2)}</Typography>
                  </Box>
                )) : (
                  <Box sx={{ p: 3, borderRadius: 2, bgcolor: '#F8FAFC', textAlign: 'center' }}>
                    <Typography sx={{ color: '#64748B' }}>No recent activity yet</Typography>
                  </Box>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
