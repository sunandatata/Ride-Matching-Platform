import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { rideService } from '@/services/rideService'
import { RideRequest } from '@/types'

export const useRideHistory = (page = 1, perPage = 10) => useQuery({
  queryKey: ['rides', 'history', page, perPage],
  queryFn: () => rideService.getRideHistory(page, perPage),
  staleTime: 1000 * 60 * 5,
})

export const useCurrentRide = () => useQuery({
  queryKey: ['rides', 'current'],
  queryFn: () => rideService.getCurrentRide(),
  staleTime: 0,
  refetchInterval: 1000, // Refetch every 1 second to immediately catch driver accepting ride
})

export const useRequestRide = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: RideRequest) => rideService.requestRide(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['rides'] }) },
  })
}

export const useCancelRide = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ rideId, reason }: { rideId: string; reason: string }) => rideService.cancelRide(rideId, reason),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['rides'] }) },
  })
}

export const useRateRide = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ rideId, rating, feedback }: { rideId: string; rating: number; feedback?: string }) => rideService.rateRide(rideId, rating, feedback),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['rides'] }) },
  })
}
