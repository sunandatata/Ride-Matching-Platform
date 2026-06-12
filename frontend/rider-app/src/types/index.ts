export interface LoginRequest { email?: string; phone: string; password: string; role?: 'rider' }
export interface RegisterRequest { name: string; email: string; phone: string; password: string; role?: 'rider' }
export interface AuthResponse { access_token: string; refresh_token: string; user: User }
export interface User { id: string; phone: string; email?: string; role: 'rider'; name: string; verified: boolean }
export type RideStatus = 'requested' | 'driver_assigned' | 'driver_accepted' | 'driver_arriving' | 'driver_arrived' | 'trip_started' | 'in_progress' | 'completed' | 'cancelled'
export interface RideTimelineItem { key: string; completed: boolean; completed_at?: string | null }
export interface Ride { id: string; rider_id: string; driver_id?: string; driver_name?: string; driver_rating?: number; vehicle?: string; vehicle_details?: Vehicle; license_plate?: string; eta_seconds?: number; pickup_location: Location; dropoff_location: Location; status: RideStatus; distance: number; estimated_fare: number; actual_fare?: number; rating?: number; feedback?: string; created_at: string; updated_at: string; completed_at?: string; driver?: Driver; timeline?: RideTimelineItem[] }
export interface Driver { id: string; name: string; phone: string; rating: number; vehicle?: Vehicle }
export interface Vehicle { license_plate: string; make: string; model: string; year: number; color: string }
export interface Location { latitude: number; longitude: number; address: string }
export interface PaymentMethod { id: string; type: 'credit' | 'debit' | 'upi' | 'wallet'; last4?: string; default: boolean }
export interface Review { id: string; ride_id: string; rating: number; comment?: string; created_at: string }
export interface RideRequest { pickup_location: Location; dropoff_location: Location }
