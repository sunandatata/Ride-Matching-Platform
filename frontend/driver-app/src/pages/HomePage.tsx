import React, { useEffect, useMemo, useState } from 'react'
import {
  Avatar,
  Box,
  Button,
  CircularProgress,
  Typography,
} from '@mui/material'
import {
  AccessTime as TimeIcon,
  AccountCircle as AccountIcon,
  Call as CallIcon,
  CheckCircle as CheckIcon,
  Close as CloseIcon,
  DirectionsCar as CarIcon,
  Flag as FlagIcon,
  LocalTaxi as TaxiIcon,
  Navigation as NavigationIcon,
  Paid as PaidIcon,
  PersonPinCircle as PickupIcon,
  Route as RouteIcon,
  Star as StarIcon,
  Today as TodayIcon,
  TrendingUp as TrendingIcon,
} from '@mui/icons-material'
import {
  useAcceptRide,
  useArriveRide,
  useAvailableRides,
  useCompleteRide,
  useCurrentRide,
  useDriverEarnings,
  useRideHistory,
  useStartRide,
} from '@/hooks/useRides'
import { useAuth } from '@/hooks/useAuth'
import { useRideStore } from '@/store/rideStore'
import { locationService } from '@/services/locationService'
import { Location, Ride } from '@/types'
import '../styles/HomePage.css'

type DriverStage = 'available' | 'pickup' | 'arrived' | 'trip' | 'complete'

const round = (value: number, places = 2) => {
  const factor = Math.pow(10, places)
  return Math.round(value * factor) / factor
}

const toRadians = (value: number) => value * Math.PI / 180

const calculateDistance = (from: Location, to: Location) => {
  const radiusKm = 6371
  const dLat = toRadians(to.latitude - from.latitude)
  const dLon = toRadians(to.longitude - from.longitude)
  const fromLat = toRadians(from.latitude)
  const toLat = toRadians(to.latitude)
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
    + Math.cos(fromLat) * Math.cos(toLat) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return radiusKm * c
}

const getDriverStage = (ride: Ride | null): DriverStage => {
  if (!ride) return 'available'
  if (ride.status === 'completed') return 'complete'
  if (ride.status === 'driver_arrived') return 'arrived'
  if (ride.status === 'trip_started' || ride.status === 'in_progress') return 'trip'
  return 'pickup'
}

const estimateEarnings = (ride: Ride) => round((ride.actual_fare || ride.estimated_fare) * 0.85)

const fallbackDriverLocation = (ride?: Ride | null): Location => ({
  latitude: round((ride?.pickup_location.latitude || 40.7527) + 0.01, 6),
  longitude: round((ride?.pickup_location.longitude || -73.9772) - 0.012, 6),
  address: 'Driver location',
})

const getPointStyle = (point: Location, points: Location[]) => {
  const latitudes = points.map((item) => item.latitude)
  const longitudes = points.map((item) => item.longitude)
  let minLat = Math.min(...latitudes)
  let maxLat = Math.max(...latitudes)
  let minLon = Math.min(...longitudes)
  let maxLon = Math.max(...longitudes)
  const latPadding = Math.max((maxLat - minLat) * 0.26, 0.01)
  const lonPadding = Math.max((maxLon - minLon) * 0.26, 0.01)
  minLat -= latPadding
  maxLat += latPadding
  minLon -= lonPadding
  maxLon += lonPadding

  const left = ((point.longitude - minLon) / (maxLon - minLon)) * 100
  const top = (1 - ((point.latitude - minLat) / (maxLat - minLat))) * 100

  return {
    left: `${Math.min(92, Math.max(8, left))}%`,
    top: `${Math.min(88, Math.max(12, top))}%`,
  }
}

const DriverRouteMap: React.FC<{
  ride: Ride
  driverLocation: Location
  stage: DriverStage
}> = ({ ride, driverLocation, stage }) => {
  const routeStart = stage === 'trip' || stage === 'complete' ? ride.pickup_location : driverLocation
  const routeEnd = stage === 'trip' || stage === 'complete' ? ride.dropoff_location : ride.pickup_location
  const points = [driverLocation, ride.pickup_location, ride.dropoff_location, routeStart, routeEnd]
  const start = getPointStyle(routeStart, points)
  const end = getPointStyle(routeEnd, points)

  return (
    <Box className="driver-map">
      <svg className="driver-map-route">
        <line
          x1={start.left}
          y1={start.top}
          x2={end.left}
          y2={end.top}
          stroke={stage === 'trip' || stage === 'complete' ? '#00C875' : '#00D9FF'}
          strokeWidth="5"
          strokeLinecap="round"
          strokeDasharray={stage === 'pickup' ? '10 12' : '0'}
        />
        <line
          x1={getPointStyle(ride.pickup_location, points).left}
          y1={getPointStyle(ride.pickup_location, points).top}
          x2={getPointStyle(ride.dropoff_location, points).left}
          y2={getPointStyle(ride.dropoff_location, points).top}
          stroke="rgba(255,255,255,0.22)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray="7 10"
        />
      </svg>

      <Box className="driver-map-marker car" style={getPointStyle(driverLocation, points)}>
        <CarIcon fontSize="small" />
      </Box>
      <Box className="driver-map-marker pickup" style={getPointStyle(ride.pickup_location, points)}>
        <PickupIcon fontSize="small" />
      </Box>
      <Box className="driver-map-marker dropoff" style={getPointStyle(ride.dropoff_location, points)}>
        <FlagIcon fontSize="small" />
      </Box>
    </Box>
  )
}

const LocationStack: React.FC<{ ride: Ride }> = ({ ride }) => (
  <Box className="location-stack">
    <Box className="location-row">
      <span className="location-dot pickup" />
      <Box>
        <Typography className="location-label">Pickup</Typography>
        <Typography className="location-address">{ride.pickup_location.address}</Typography>
      </Box>
    </Box>
    <span className="location-connector" />
    <Box className="location-row">
      <span className="location-dot dropoff" />
      <Box>
        <Typography className="location-label">Destination</Typography>
        <Typography className="location-address">{ride.dropoff_location.address}</Typography>
      </Box>
    </Box>
  </Box>
)

export const HomePage: React.FC = () => {
  const [declinedRideIds, setDeclinedRideIds] = useState<string[]>([])
  const [driverLocation, setDriverLocation] = useState<Location | null>(null)
  const [lastCompletedRide, setLastCompletedRide] = useState<Ride | null>(null)

  const { user } = useAuth()
  const { data: availableRides, isLoading: ridesLoading } = useAvailableRides()
  const { data: serverCurrentRide } = useCurrentRide()
  const { data: earnings } = useDriverEarnings()
  const { data: rideHistory } = useRideHistory(1, 20)
  const acceptRideMutation = useAcceptRide()
  const arriveRideMutation = useArriveRide()
  const startRideMutation = useStartRide()
  const completeRideMutation = useCompleteRide()
  const { currentRide: storedCurrentRide, setOnlineStatus } = useRideStore()
  const currentRide = serverCurrentRide || storedCurrentRide
  const stage = getDriverStage(currentRide || null)
  const activeDriverLocation = driverLocation || fallbackDriverLocation(currentRide)
  const visibleRides = useMemo(
    () => (availableRides || []).filter((ride) => !declinedRideIds.includes(ride.id)),
    [availableRides, declinedRideIds]
  )
  const todayKey = new Date().toISOString().slice(0, 10)
  const todayTrips = rideHistory?.data?.filter((ride) => ride.created_at?.slice(0, 10) === todayKey).length || 0
  const rideActionPending = arriveRideMutation.isPending || startRideMutation.isPending || completeRideMutation.isPending

  useEffect(() => {
    setOnlineStatus(true)
    locationService.startLocationTracking().catch(() => undefined)

    const updateBrowserLocation = () => {
      if (!navigator.geolocation) return
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const nextLocation = {
            latitude: round(position.coords.latitude, 6),
            longitude: round(position.coords.longitude, 6),
            address: 'Live driver location',
          }
          setDriverLocation(nextLocation)
          locationService.updateLocation(nextLocation.latitude, nextLocation.longitude).catch(() => undefined)
        },
        () => undefined,
        { enableHighAccuracy: true, timeout: 8000 }
      )
    }

    updateBrowserLocation()
    const interval = window.setInterval(updateBrowserLocation, 5000)
    return () => window.clearInterval(interval)
  }, [setOnlineStatus])

  const getPickupMetrics = (ride: Ride, index = 0) => {
    const distanceToPickup = driverLocation
      ? calculateDistance(driverLocation, ride.pickup_location)
      : Math.max(0.8, ride.distance * 0.18 + 0.4 + (index * 0.2))
    const eta = Math.max(2, Math.ceil(distanceToPickup * 4 + 2))
    return { distanceToPickup: round(distanceToPickup, 1), eta }
  }

  const handleAcceptRide = (ride: Ride) => {
    setLastCompletedRide(null)
    acceptRideMutation.mutate(ride.id)
  }

  const handleDeclineRide = (rideId: string) => {
    setDeclinedRideIds((ids) => [...ids, rideId])
  }

  const handleArriveRide = () => {
    if (currentRide) arriveRideMutation.mutate(currentRide.id)
  }

  const handleStartRide = () => {
    if (currentRide) startRideMutation.mutate(currentRide.id)
  }

  const handleCompleteRide = () => {
    if (!currentRide) return
    completeRideMutation.mutate(
      {
        rideId: currentRide.id,
        fare: currentRide.actual_fare || currentRide.estimated_fare,
      },
      {
        onSuccess: (ride) => setLastCompletedRide(ride),
      }
    )
  }

  const activeMetrics = currentRide ? getPickupMetrics(currentRide) : null
  const stats = [
    { label: 'Current earnings', value: `$${(earnings?.daily || 0).toFixed(2)}`, icon: <PaidIcon /> },
    { label: 'Today trips', value: todayTrips, icon: <TodayIcon /> },
    { label: 'Incoming requests', value: visibleRides.length, icon: <TaxiIcon /> },
    { label: 'Acceptance rate', value: '98%', icon: <TrendingIcon /> },
  ]

  return (
    <Box className="driver-home">
      <Box className="driver-hero">
        <Box className="driver-profile-card">
          <Avatar className="driver-avatar">
            {user?.name?.split(' ').map((part) => part[0]).join('').slice(0, 2) || <AccountIcon />}
          </Avatar>
          <Box className="driver-profile-copy">
            <Typography className="driver-eyebrow">Driver dashboard</Typography>
            <Typography className="driver-name">{user?.name || 'Driver'}</Typography>
            <Box className="driver-status-row">
              <span className="live-dot" />
              <Typography className="driver-status-text">Online and accepting trips</Typography>
            </Box>
          </Box>
        </Box>

        <Box className="driver-stat-grid">
          {stats.map((item) => (
            <Box className="driver-stat-card" key={item.label}>
              <span className="driver-stat-icon">{item.icon}</span>
              <Typography className="driver-stat-label">{item.label}</Typography>
              <Typography className="driver-stat-value">{item.value}</Typography>
            </Box>
          ))}
        </Box>
      </Box>

      <Box className="driver-content-grid">
        <Box className="driver-main-column">
          {currentRide && (
            <Box className="panel active-trip-panel">
              <Box className="panel-header">
                <Box>
                  <Typography className="panel-kicker">Active trip</Typography>
                  <Typography className="panel-title">
                    {stage === 'pickup' && `Navigate to pickup${activeMetrics ? ` - ${activeMetrics.eta} min` : ''}`}
                    {stage === 'arrived' && 'Rider pickup ready'}
                    {stage === 'trip' && 'Trip in progress'}
                    {stage === 'complete' && 'Trip completed'}
                  </Typography>
                </Box>
                <span className={`workflow-pill ${stage}`}>{currentRide.status.replace('_', ' ')}</span>
              </Box>

              <DriverRouteMap ride={currentRide} driverLocation={activeDriverLocation} stage={stage} />

              <Box className="active-trip-body">
                <Box className="rider-strip">
                  <Avatar className="rider-avatar">{(currentRide.rider_name || 'Rider').charAt(0)}</Avatar>
                  <Box>
                    <Typography className="rider-name">{currentRide.rider_name || 'Rider'}</Typography>
                    <Typography className="rider-subtitle">Verified rider</Typography>
                  </Box>
                  <Button className="icon-action" startIcon={<CallIcon />}>Call</Button>
                </Box>

                <LocationStack ride={currentRide} />

                <Box className="trip-metrics-row">
                  <Box>
                    <Typography className="metric-label">{stage === 'trip' ? 'Remaining' : 'Pickup ETA'}</Typography>
                    <Typography className="metric-value">
                      {stage === 'trip' ? `${Math.max(4, Math.ceil(currentRide.distance * 3))} min` : `${activeMetrics?.eta || 4} min`}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography className="metric-label">{stage === 'trip' ? 'Distance left' : 'Distance to pickup'}</Typography>
                    <Typography className="metric-value">
                      {stage === 'trip' ? `${Math.max(0.5, round(currentRide.distance * 0.62, 1))} km` : `${activeMetrics?.distanceToPickup || 1.2} km`}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography className="metric-label">Earnings</Typography>
                    <Typography className="metric-value earning">${estimateEarnings(currentRide).toFixed(2)}</Typography>
                  </Box>
                </Box>

                <Box className="driver-action-row">
                  {stage === 'pickup' && (
                    <Button className="primary-driver-action" onClick={handleArriveRide} disabled={rideActionPending}>
                      {arriveRideMutation.isPending ? <CircularProgress size={20} sx={{ color: 'inherit' }} /> : 'Mark Arrived'}
                    </Button>
                  )}
                  {stage === 'arrived' && (
                    <Button className="primary-driver-action" onClick={handleStartRide} disabled={rideActionPending}>
                      {startRideMutation.isPending ? <CircularProgress size={20} sx={{ color: 'inherit' }} /> : 'Start Trip'}
                    </Button>
                  )}
                  {stage === 'trip' && (
                    <Button className="primary-driver-action" onClick={handleCompleteRide} disabled={rideActionPending}>
                      {completeRideMutation.isPending ? <CircularProgress size={20} sx={{ color: 'inherit' }} /> : 'Complete Trip'}
                    </Button>
                  )}
                </Box>
              </Box>
            </Box>
          )}

          {lastCompletedRide && !currentRide && (
            <Box className="panel trip-summary-panel">
              <Box className="summary-icon"><CheckIcon /></Box>
              <Box>
                <Typography className="panel-title">Trip summary</Typography>
                <Typography className="panel-copy">{lastCompletedRide.dropoff_location.address}</Typography>
              </Box>
              <Box className="summary-grid">
                <Box>
                  <Typography className="metric-label">Fare</Typography>
                  <Typography className="metric-value">${(lastCompletedRide.actual_fare || lastCompletedRide.estimated_fare).toFixed(2)}</Typography>
                </Box>
                <Box>
                  <Typography className="metric-label">Earnings</Typography>
                  <Typography className="metric-value earning">${estimateEarnings(lastCompletedRide).toFixed(2)}</Typography>
                </Box>
                <Box>
                  <Typography className="metric-label">Distance</Typography>
                  <Typography className="metric-value">{lastCompletedRide.distance.toFixed(1)} km</Typography>
                </Box>
              </Box>
            </Box>
          )}

          <Box className="panel">
            <Box className="panel-header">
              <Box>
                <Typography className="panel-kicker">Incoming ride requests</Typography>
                <Typography className="panel-title">Available now</Typography>
              </Box>
              {ridesLoading && <CircularProgress size={22} sx={{ color: 'var(--color-accent-electric)' }} />}
            </Box>

            {currentRide ? (
              <Box className="empty-request-state">
                <NavigationIcon />
                <Typography className="empty-title">Active trip in progress</Typography>
                <Typography className="empty-text">New requests will remain available after this trip is complete.</Typography>
              </Box>
            ) : ridesLoading ? (
              <Box className="loading-state">
                <CircularProgress sx={{ color: 'var(--color-accent-electric)' }} />
                <Typography className="loading-text">Refreshing requests...</Typography>
              </Box>
            ) : visibleRides.length > 0 ? (
              <Box className="request-list">
                {visibleRides.map((ride, index) => {
                  const metrics = getPickupMetrics(ride, index)
                  return (
                    <Box className="request-card" key={ride.id}>
                      <Box className="request-card-header">
                        <Box>
                          <Typography className="request-rider">{ride.rider_name || 'Rider'}</Typography>
                          <Typography className="request-subtitle">Ride request</Typography>
                        </Box>
                        <Typography className="request-earning">${estimateEarnings(ride).toFixed(2)}</Typography>
                      </Box>

                      <LocationStack ride={ride} />

                      <Box className="request-metrics">
                        <span><RouteIcon /> {metrics.distanceToPickup} km pickup</span>
                        <span><TimeIcon /> {metrics.eta} min</span>
                        <span><StarIcon /> ${ride.estimated_fare.toFixed(2)} fare</span>
                      </Box>

                      <Box className="request-actions">
                        <Button
                          className="decline-button"
                          startIcon={<CloseIcon />}
                          onClick={() => handleDeclineRide(ride.id)}
                          disabled={acceptRideMutation.isPending}
                        >
                          Decline
                        </Button>
                        <Button
                          className="accept-button"
                          startIcon={<CheckIcon />}
                          onClick={() => handleAcceptRide(ride)}
                          disabled={acceptRideMutation.isPending}
                        >
                          {acceptRideMutation.isPending ? <CircularProgress size={18} sx={{ color: 'inherit' }} /> : 'Accept Ride'}
                        </Button>
                      </Box>
                    </Box>
                  )
                })}
              </Box>
            ) : (
              <Box className="empty-request-state">
                <TaxiIcon />
                <Typography className="empty-title">No requests nearby</Typography>
                <Typography className="empty-text">The dashboard refreshes automatically.</Typography>
              </Box>
            )}
          </Box>
        </Box>

        <Box className="driver-side-column">
          <Box className="panel compact-panel">
            <Typography className="panel-kicker">Workflow</Typography>
            <Typography className="panel-title">Ride progress</Typography>
            <Box className="workflow-list">
              {[
                ['Accept', Boolean(currentRide) || Boolean(lastCompletedRide)],
                ['Navigate', stage === 'pickup' || stage === 'arrived' || stage === 'trip' || stage === 'complete' || Boolean(lastCompletedRide)],
                ['Pickup', stage === 'arrived' || stage === 'trip' || stage === 'complete' || Boolean(lastCompletedRide)],
                ['Dropoff', stage === 'trip' || stage === 'complete' || Boolean(lastCompletedRide)],
                ['Paid', stage === 'complete' || Boolean(lastCompletedRide)],
              ].map(([label, complete]) => (
                <Box className={`workflow-step ${complete ? 'complete' : ''}`} key={String(label)}>
                  {complete ? <CheckIcon /> : <span />}
                  <Typography>{label}</Typography>
                </Box>
              ))}
            </Box>
          </Box>

          <Box className="panel compact-panel">
            <Typography className="panel-kicker">Live status</Typography>
            <Box className="status-metric">
              <span className="live-dot large" />
              <Box>
                <Typography className="status-title">Available</Typography>
                <Typography className="status-copy">Requests refresh every second</Typography>
              </Box>
            </Box>
            <Box className="status-metric">
              <NavigationIcon />
              <Box>
                <Typography className="status-title">Location</Typography>
                <Typography className="status-copy">
                  {driverLocation ? `${driverLocation.latitude.toFixed(4)}, ${driverLocation.longitude.toFixed(4)}` : 'Waiting for GPS'}
                </Typography>
              </Box>
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  )
}
