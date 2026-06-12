import { apiClient } from './api'
import { PaymentMethod } from '@/types'

export const paymentService = {
  getPaymentMethods: async (): Promise<PaymentMethod[]> => (await apiClient.get<PaymentMethod[]>('/api/v1/riders/payment-methods')).data,
  addPaymentMethod: async (data: any): Promise<PaymentMethod> => (await apiClient.post<PaymentMethod>('/api/v1/riders/payment-methods', data)).data,
  setDefaultPaymentMethod: async (methodId: string): Promise<void> => { await apiClient.post(`/api/v1/riders/payment-methods/${methodId}/default`) },
  deletePaymentMethod: async (methodId: string): Promise<void> => { await apiClient.delete(`/api/v1/riders/payment-methods/${methodId}`) },
}
