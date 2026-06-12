import { apiClient } from './api'
import { Driver, PaginatedResponse, DriverFilters, Vehicle } from '@/types'

export const driverService = {
  getDrivers: async (filters?: DriverFilters): Promise<PaginatedResponse<Driver>> => {
    const response = await apiClient.get<PaginatedResponse<Driver>>('/api/v1/drivers', {
      params: filters,
    })
    return response.data
  },

  getDriver: async (driverId: string): Promise<Driver> => {
    const response = await apiClient.get<Driver>(`/api/v1/drivers/${driverId}`)
    return response.data
  },

  updateDriver: async (driverId: string, data: Partial<Driver>): Promise<Driver> => {
    const response = await apiClient.put<Driver>(`/api/v1/drivers/${driverId}`, data)
    return response.data
  },

  approveDriver: async (driverId: string): Promise<Driver> => {
    const response = await apiClient.put<Driver>(
      `/api/v1/drivers/${driverId}/approval`,
      { status: 'approved' }
    )
    return response.data
  },

  rejectDriver: async (driverId: string, reason: string): Promise<Driver> => {
    const response = await apiClient.put<Driver>(
      `/api/v1/drivers/${driverId}/approval`,
      { status: 'rejected', reason }
    )
    return response.data
  },

  suspendDriver: async (driverId: string, reason: string): Promise<Driver> => {
    const response = await apiClient.put<Driver>(
      `/api/v1/drivers/${driverId}/suspension`,
      { status: 'suspended', reason }
    )
    return response.data
  },

  reactivateDriver: async (driverId: string): Promise<Driver> => {
    const response = await apiClient.put<Driver>(
      `/api/v1/drivers/${driverId}/suspension`,
      { status: 'active' }
    )
    return response.data
  },

  getDriverVehicle: async (driverId: string): Promise<Vehicle> => {
    const response = await apiClient.get<Vehicle>(`/api/v1/drivers/${driverId}/vehicle`)
    return response.data
  },

  updateVehicle: async (driverId: string, vehicleData: Partial<Vehicle>): Promise<Vehicle> => {
    const response = await apiClient.put<Vehicle>(
      `/api/v1/drivers/${driverId}/vehicle`,
      vehicleData
    )
    return response.data
  },
}
