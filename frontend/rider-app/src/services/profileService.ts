import { apiClient } from './api'
import { User } from '@/types'

export const profileService = {
  getProfile: async (): Promise<User> => (await apiClient.get<User>('/api/v1/riders/profile')).data,
  updateProfile: async (data: Partial<User>): Promise<User> => (await apiClient.put<User>('/api/v1/riders/profile', data)).data,
  uploadProfilePicture: async (file: File): Promise<{ url: string }> => {
    const formData = new FormData()
    formData.append('file', file)
    return (await apiClient.post<{ url: string }>('/api/v1/riders/profile-picture', formData, { headers: { 'Content-Type': 'multipart/form-data' } })).data
  },
}
