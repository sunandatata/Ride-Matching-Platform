import { apiClient } from './api'

export const locationService = {
  updateLocation: async (latitude: number, longitude: number): Promise<void> => {
    await apiClient.post('/api/v1/locations/update', { latitude, longitude, timestamp: new Date().toISOString() })
  },
  startLocationTracking: async (): Promise<void> => {
    await apiClient.post('/api/v1/drivers/tracking/start', {})
  },
  stopLocationTracking: async (): Promise<void> => {
    await apiClient.post('/api/v1/drivers/tracking/stop', {})
  },
}
