import { apiClient } from './api'
import { Ride, PaginatedResponse, RideFilters } from '@/types'

export const rideService = {
  getRides: async (filters?: RideFilters): Promise<PaginatedResponse<Ride>> => {
    const response = await apiClient.get<PaginatedResponse<Ride>>('/api/v1/rides', {
      params: filters,
    })
    return response.data
  },

  getRide: async (rideId: string): Promise<Ride> => {
    const response = await apiClient.get<Ride>(`/api/v1/rides/${rideId}`)
    return response.data
  },

  updateRideStatus: async (
    rideId: string,
    status: Ride['status']
  ): Promise<Ride> => {
    const response = await apiClient.put<Ride>(`/api/v1/rides/${rideId}/status`, {
      status,
    })
    return response.data
  },

  cancelRide: async (rideId: string, reason: string): Promise<Ride> => {
    const response = await apiClient.post<Ride>(
      `/api/v1/rides/${rideId}/cancel`,
      { reason }
    )
    return response.data
  },
}
