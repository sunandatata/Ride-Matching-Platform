// Auth Types
export interface LoginRequest {
  phone: string
  password: string
  role?: 'admin'
}

export interface AuthResponse {
  access_token: string
  refresh_token: string
  user: User
}

export interface User {
  id: string
  phone: string
  email?: string
  role: 'admin' | 'driver' | 'rider'
  name: string
  verified: boolean
}

// Driver Types
export interface Driver {
  id: string
  user_id: string
  name: string
  phone: string
  email?: string
  vehicle_id?: string
  status: 'active' | 'inactive' | 'suspended'
  approval_status: 'pending' | 'approved' | 'rejected'
  rating: number
  total_rides: number
  total_earnings: number
  document_verified: boolean
  created_at: string
  updated_at: string
}

export interface DriverStats {
  total_drivers: number
  active_drivers: number
  pending_approvals: number
  suspended_drivers: number
  average_rating: number
}

export interface Vehicle {
  id: string
  driver_id: string
  license_plate: string
  make: string
  model: string
  year: number
  color: string
  vehicle_type: string
  capacity: number
  insurance_expiry: string
  verified: boolean
}

// Ride Types
export interface Ride {
  id: string
  rider_id: string
  driver_id?: string
  pickup_location: Location
  dropoff_location: Location
  status: 'requested' | 'driver_assigned' | 'driver_accepted' | 'driver_arriving' | 'driver_arrived' | 'trip_started' | 'in_progress' | 'completed' | 'cancelled'
  distance: number
  estimated_fare: number
  actual_fare?: number
  rating?: number
  feedback?: string
  created_at: string
  updated_at: string
  completed_at?: string
}

export interface Location {
  latitude: number
  longitude: number
  address: string
}

export interface RideStats {
  total_rides: number
  completed_rides: number
  cancelled_rides: number
  total_revenue: number
  average_rating: number
}

// Pagination
export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  per_page: number
  total_pages: number
}

// Dashboard Metrics
export interface PlatformMetrics {
  active_drivers: number
  active_riders: number
  total_rides_today: number
  total_revenue_today: number
  pending_approvals: number
  average_ride_rating: number
  system_health: {
    api_status: 'healthy' | 'degraded' | 'down'
    database_status: 'healthy' | 'degraded' | 'down'
    queue_length: number
  }
}

export interface RideFilters {
  status?: string
  start_date?: string
  end_date?: string
  page?: number
  per_page?: number
}

export interface DriverFilters {
  status?: string
  approval_status?: string
  page?: number
  per_page?: number
}
