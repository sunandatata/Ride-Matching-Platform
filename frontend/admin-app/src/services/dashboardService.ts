import { apiClient } from './api'
import { PlatformMetrics, RideStats, DriverStats } from '@/types'

export const dashboardService = {
  getPlatformMetrics: async (): Promise<PlatformMetrics> => {
    const response = await apiClient.get<PlatformMetrics>('/api/v1/admin/metrics')
    return response.data
  },

  getRideStats: async (startDate?: string, endDate?: string): Promise<RideStats> => {
    const response = await apiClient.get<RideStats>('/api/v1/admin/rides/stats', {
      params: { start_date: startDate, end_date: endDate },
    })
    return response.data
  },

  getDriverStats: async (): Promise<DriverStats> => {
    const response = await apiClient.get<DriverStats>('/api/v1/admin/drivers/stats')
    return response.data
  },

  getRevenueChartData: async (period: 'day' | 'week' | 'month' = 'month') => {
    const response = await apiClient.get('/api/v1/admin/analytics/revenue', {
      params: { period },
    })
    return response.data
  },

  getRideChartData: async (period: 'day' | 'week' | 'month' = 'month') => {
    const response = await apiClient.get('/api/v1/admin/analytics/rides', {
      params: { period },
    })
    return response.data
  },
}
