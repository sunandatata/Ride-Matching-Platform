import React, { useState } from 'react'
import {
  Box,
  Typography,
  Card,
  CardContent,
  Grid,
  ToggleButton,
  ToggleButtonGroup,
  CircularProgress,
} from '@mui/material'
import { useRideChart, useRevenueChart } from '@/hooks/useDashboard'
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export const AnalyticsPage: React.FC = () => {
  const [period, setPeriod] = useState<'day' | 'week' | 'month'>('month')
  const { data: rideChartData, isLoading: rideChartLoading } = useRideChart(period)
  const { data: revenueChartData, isLoading: revenueChartLoading } = useRevenueChart(period)

  const handlePeriodChange = (
    _event: React.MouseEvent<HTMLElement>,
    newPeriod: 'day' | 'week' | 'month' | null
  ) => {
    if (newPeriod) {
      setPeriod(newPeriod)
    }
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" sx={{ fontWeight: 'bold', mb: 3 }}>
        Analytics & Reporting
      </Typography>

      <Box sx={{ mb: 3 }}>
        <ToggleButtonGroup
          value={period}
          exclusive
          onChange={handlePeriodChange}
          size="small"
        >
          <ToggleButton value="day">Daily</ToggleButton>
          <ToggleButton value="week">Weekly</ToggleButton>
          <ToggleButton value="month">Monthly</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                Ride Activity Trend
              </Typography>
              {rideChartLoading ? (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={300}>
                  <CircularProgress />
                </Box>
              ) : rideChartData?.length ? (
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={rideChartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="rides" fill="#1976d2" />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={300}>
                  <Typography color="textSecondary">No data</Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                Revenue Trend
              </Typography>
              {revenueChartLoading ? (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={300}>
                  <CircularProgress />
                </Box>
              ) : revenueChartData?.length ? (
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={revenueChartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" />
                    <YAxis />
                    <Tooltip />
                    <Line type="monotone" dataKey="revenue" stroke="#4caf50" />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <Box display="flex" justifyContent="center" alignItems="center" minHeight={300}>
                  <Typography color="textSecondary">No data</Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
