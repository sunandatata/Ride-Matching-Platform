import { apiClient } from './api'
import { Vehicle, Document, DriverStats } from '@/types'

export const driverService = {
  getDriverStats: async (): Promise<DriverStats> => (await apiClient.get<DriverStats>('/api/v1/drivers/stats')).data,
  getVehicle: async (): Promise<Vehicle> => (await apiClient.get<Vehicle>('/api/v1/drivers/vehicle')).data,
  updateVehicle: async (data: Partial<Vehicle>): Promise<Vehicle> => (await apiClient.put<Vehicle>('/api/v1/drivers/vehicle', data)).data,
  getDocuments: async (): Promise<Document[]> => (await apiClient.get<Document[]>('/api/v1/drivers/documents')).data,
  uploadDocument: async (type: string, file: File): Promise<Document> => {
    const formData = new FormData()
    formData.append('type', type)
    formData.append('file', file)
    return (await apiClient.post<Document>('/api/v1/drivers/documents', formData, { headers: { 'Content-Type': 'multipart/form-data' } })).data
  },
  updateDocumentExpiry: async (documentId: string, expiryDate: string): Promise<Document> => (await apiClient.put<Document>(`/api/v1/drivers/documents/${documentId}`, { expiry_date: expiryDate })).data,
  deleteDocument: async (documentId: string): Promise<void> => { await apiClient.delete(`/api/v1/drivers/documents/${documentId}`) },
}
