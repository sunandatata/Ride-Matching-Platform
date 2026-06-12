import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { rideService } from '@/services/rideService'
import { RideFilters } from '@/types'

export const useRides = (filters?: RideFilters) => {
  return useQuery({
    queryKey: ['rides', filters],
    queryFn: () => rideService.getRides(filters),
    staleTime: 0,
    refetchInterval: 2000,
  })
}

export const useRide = (rideId: string) => {
  return useQuery({
    queryKey: ['rides', rideId],
    queryFn: () => rideService.getRide(rideId),
    staleTime: 0,
    refetchInterval: 2000,
  })
}

export const useCancelRide = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ rideId, reason }: { rideId: string; reason: string }) =>
      rideService.cancelRide(rideId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rides'] })
    },
  })
}
