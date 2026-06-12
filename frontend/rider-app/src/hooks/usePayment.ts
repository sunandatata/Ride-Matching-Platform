import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { paymentService } from '@/services/paymentService'

export const usePaymentMethods = () => useQuery({
  queryKey: ['payment-methods'],
  queryFn: () => paymentService.getPaymentMethods(),
  staleTime: 1000 * 60 * 5,
})

export const useAddPaymentMethod = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: any) => paymentService.addPaymentMethod(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['payment-methods'] }) },
  })
}

export const useDeletePaymentMethod = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (methodId: string) => paymentService.deletePaymentMethod(methodId),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['payment-methods'] }) },
  })
}
