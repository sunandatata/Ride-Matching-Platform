export interface LoginRequest { email?: string; phone: string; password: string; role?: 'driver' }
export interface DriverVehicleRegistration { make: string; model: string; year: number; color: string; license_plate: string; vehicle_type?: string }
export interface RegisterRequest { name: string; email: string; phone: string; password: string; role?: 'driver'; vehicle: DriverVehicleRegistration }
export interface AuthResponse { access_token: string; refresh_token: string; user: User }
export interface User { id: string; phone: string; email?: string; role: 'driver'; name: string; verified: boolean }
export type RideStatus = 'requested' | 'driver_assigned' | 'driver_accepted' | 'driver_arriving' | 'driver_arrived' | 'trip_started' | 'in_progress' | 'completed' | 'cancelled'
export interface Ride { id: string; rider_id: string; rider_name?: string; driver_id?: string; driver_name?: string; driver_rating?: number; vehicle?: string; license_plate?: string; pickup_location: Location; dropoff_location: Location; status: RideStatus; distance: number; estimated_fare: number; actual_fare?: number; rating?: number; feedback?: string; created_at: string; updated_at: string; completed_at?: string }
export interface Location { latitude: number; longitude: number; address: string }
export interface Vehicle { id: string; driver_id: string; license_plate: string; make: string; model: string; year: number; color: string; capacity: number; insurance_expiry: string; verified: boolean }
export interface Document { id: string; driver_id: string; type: 'license' | 'insurance' | 'registration' | 'pollution'; url: string; expiry_date: string; verified: boolean; created_at: string }
export interface DriverStats { total_rides: number; completed_rides: number; rating: number; total_earnings: number; cancellation_rate: number }
export interface LocationUpdate { latitude: number; longitude: number; timestamp: string }
