import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { rideService } from '@/services/rideService'
import { useRideStore } from '@/store/rideStore'

export const useAvailableRides = () => useQuery({
  queryKey: ['rides', 'available'],
  queryFn: () => rideService.getAvailableRides(),
  staleTime: 0,
  refetchInterval: 1000, // Refetch every 1 second for immediate ride availability
})

export const useCurrentRide = () => {
  return useQuery({
    queryKey: ['rides', 'current'],
    queryFn: () => rideService.getCurrentRide(),
    staleTime: 0,
    refetchInterval: 1000, // Refetch every 1 second to catch ride status changes
  })
}

export const useAcceptRide = () => {
  const queryClient = useQueryClient()
  const { setCurrentRide } = useRideStore()
  return useMutation({
    mutationFn: (rideId: string) => rideService.acceptRide(rideId),
    onSuccess: (ride) => {
      setCurrentRide(ride)
      queryClient.invalidateQueries({ queryKey: ['rides'] })
    },
  })
}

export const useStartRide = () => {
  const queryClient = useQueryClient()
  const { setCurrentRide } = useRideStore()
  return useMutation({
    mutationFn: (rideId: string) => rideService.startRide(rideId),
    onSuccess: (ride) => {
      setCurrentRide(ride)
      queryClient.invalidateQueries({ queryKey: ['rides'] })
    },
  })
}

export const useArriveRide = () => {
  const queryClient = useQueryClient()
  const { setCurrentRide } = useRideStore()
  return useMutation({
    mutationFn: (rideId: string) => rideService.arriveRide(rideId),
    onSuccess: (ride) => {
      setCurrentRide(ride)
      queryClient.invalidateQueries({ queryKey: ['rides'] })
      queryClient.invalidateQueries({ queryKey: ['driver'] })
    },
  })
}

export const useCompleteRide = () => {
  const queryClient = useQueryClient()
  const { setCurrentRide } = useRideStore()
  return useMutation({
    mutationFn: ({ rideId, fare }: { rideId: string; fare: number }) => rideService.completeRide(rideId, fare),
    onSuccess: () => {
      setCurrentRide(null)
      queryClient.invalidateQueries({ queryKey: ['rides'] })
      queryClient.invalidateQueries({ queryKey: ['driver'] })
    },
  })
}

export const useRideHistory = (page = 1, perPage = 10) => useQuery({
  queryKey: ['rides', 'history', page, perPage],
  queryFn: () => rideService.getRideHistory(page, perPage),
  staleTime: 0,
  refetchInterval: 2000,
})

export const useDriverEarnings = () => useQuery({
  queryKey: ['driver', 'earnings'],
  queryFn: () => rideService.getEarnings(),
  staleTime: 0,
  refetchInterval: 2000, // Refetch every 2 seconds to show updated earnings
})

export const useEarningsHistory = () => useQuery({
  queryKey: ['driver', 'earnings', 'history'],
  queryFn: () => rideService.getEarningsHistory(),
  staleTime: 1000 * 60, // 1 minute cache
})
