import { apiClient } from './api'
import { Ride } from '@/types'

export const rideService = {
  getAvailableRides: async (): Promise<Ride[]> => {
    return (await apiClient.get<Ride[]>('/api/v1/rides/available')).data
  },
  getCurrentRide: async (): Promise<Ride | null> => {
    try { return (await apiClient.get<Ride>('/api/v1/rides/current')).data } catch { return null }
  },
  acceptRide: async (rideId: string): Promise<Ride> => {
    return (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/accept`, {})).data
  },
  arriveRide: async (rideId: string): Promise<Ride> => (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/arrived`, {})).data,
  startRide: async (rideId: string): Promise<Ride> => (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/start`, {})).data,
  completeRide: async (rideId: string, fare: number): Promise<Ride> => (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/complete`, { fare })).data,
  cancelRide: async (rideId: string, reason: string): Promise<Ride> => (await apiClient.post<Ride>(`/api/v1/rides/${rideId}/cancel`, { reason })).data,
  getRideHistory: async (page = 1, perPage = 10): Promise<{ data: Ride[]; total: number }> => (await apiClient.get('/api/v1/drivers/rides', { params: { page, per_page: perPage } })).data,
  getEarnings: async (): Promise<{ total: number; daily: number; weekly: number; rideCount: number }> => {
    try {
      return (await apiClient.get('/api/v1/drivers/earnings')).data
    } catch {
      return { total: 0, daily: 0, weekly: 0, rideCount: 0 }
    }
  },
  getEarningsHistory: async (): Promise<{ data: any[]; total: number; totalEarnings: number }> => {
    try {
      return (await apiClient.get('/api/v1/drivers/earnings/history')).data
    } catch {
      return { data: [], total: 0, totalEarnings: 0 }
    }
  },
}
