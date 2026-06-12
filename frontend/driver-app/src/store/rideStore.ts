import { create } from 'zustand'
import { Ride } from '@/types'

interface RideState {
  currentRide: Ride | null
  isOnline: boolean
  setCurrentRide: (ride: Ride | null) => void
  setOnlineStatus: (isOnline: boolean) => void
}

export const useRideStore = create<RideState>((set) => ({
  currentRide: null,
  isOnline: true,
  setCurrentRide: (ride) => set({ currentRide: ride }),
  setOnlineStatus: (isOnline) => set({ isOnline }),
}))
