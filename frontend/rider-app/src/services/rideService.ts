import { apiClient } from './api'
import { Ride, RideRequest } from '@/types'

export const rideService = {
  requestRide: async (data: RideRequest): Promise<Ride> => {
    const response = await apiClient.post<Ride>('/api/v1/rides', data)
    return response.data
  },
  getRideHistory: async (page = 1, perPage = 10): Promise<{ data: Ride[]; total: number }> => {
    try {
      return (await apiClient.get('/api/v1/rides', { params: { page, per_page: perPage } })).data
    } catch {
      return { data: [], total: 0 }
    }
  },
  getCurrentRide: async (): Promise<Ride | null> => {
    try {
      return (await apiClient.get<Ride>('/api/v1/rides/current')).data
    } catch {
      return null
    }
  },
  getRide: async (rideId: string): Promise<Ride> => {
    try {
      return (await apiClient.get<Ride>(`/api/v1/rides/${rideId}`)).data
    } catch {
      throw new Error('Ride not found')
    }
  },
  cancelRide: async (rideId: string, reason: string): Promise<Ride> => {
    try {
      return (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/cancel`, { reason })).data
    } catch {
      throw new Error('Failed to cancel ride')
    }
  },
  rateRide: async (rideId: string, rating: number, feedback?: string): Promise<Ride> => {
    try {
      return (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/rate`, { rating, feedback })).data
    } catch {
      throw new Error('Failed to rate ride')
    }
  },
}
