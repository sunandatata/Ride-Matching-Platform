import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { driverService } from '@/services/driverService'
import { DriverFilters } from '@/types'

export const useDrivers = (filters?: DriverFilters) => {
  return useQuery({
    queryKey: ['drivers', filters],
    queryFn: () => driverService.getDrivers(filters),
    staleTime: 0,
    refetchInterval: 3000,
  })
}

export const useDriver = (driverId: string) => {
  return useQuery({
    queryKey: ['drivers', driverId],
    queryFn: () => driverService.getDriver(driverId),
    staleTime: 0,
    refetchInterval: 3000,
  })
}

export const useApproveDriver = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (driverId: string) => driverService.approveDriver(driverId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drivers'] })
    },
  })
}

export const useRejectDriver = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ driverId, reason }: { driverId: string; reason: string }) =>
      driverService.rejectDriver(driverId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drivers'] })
    },
  })
}

export const useSuspendDriver = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ driverId, reason }: { driverId: string; reason: string }) =>
      driverService.suspendDriver(driverId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drivers'] })
    },
  })
}
