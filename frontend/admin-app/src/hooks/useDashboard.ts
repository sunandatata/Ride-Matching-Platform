import { useQuery } from '@tanstack/react-query'
import { dashboardService } from '@/services/dashboardService'

export const usePlatformMetrics = () => {
  return useQuery({
    queryKey: ['metrics', 'platform'],
    queryFn: () => dashboardService.getPlatformMetrics(),
    staleTime: 0,
    refetchInterval: 2000,
  })
}

export const useRideStats = (startDate?: string, endDate?: string) => {
  return useQuery({
    queryKey: ['stats', 'rides', startDate, endDate],
    queryFn: () => dashboardService.getRideStats(startDate, endDate),
    staleTime: 0,
    refetchInterval: 2000,
  })
}

export const useDriverStats = () => {
  return useQuery({
    queryKey: ['stats', 'drivers'],
    queryFn: () => dashboardService.getDriverStats(),
    staleTime: 0,
    refetchInterval: 2000,
  })
}

export const useRevenueChart = (period: 'day' | 'week' | 'month' = 'month') => {
  return useQuery({
    queryKey: ['analytics', 'revenue', period],
    queryFn: () => dashboardService.getRevenueChartData(period),
    staleTime: 0,
    refetchInterval: 3000,
  })
}

export const useRideChart = (period: 'day' | 'week' | 'month' = 'month') => {
  return useQuery({
    queryKey: ['analytics', 'rides', period],
    queryFn: () => dashboardService.getRideChartData(period),
    staleTime: 0,
    refetchInterval: 3000,
  })
}
