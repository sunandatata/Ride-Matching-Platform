import React, { useMemo, useState } from 'react'
import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  LinearProgress,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import {
  AccessTime as TimeIcon,
  CheckCircle as CheckIcon,
  DirectionsCar as CarIcon,
  Flag as FlagIcon,
  MyLocation as MyLocationIcon,
  Navigation as NavigationIcon,
  PersonPinCircle as PickupIcon,
  RadioButtonUnchecked as PendingIcon,
  ReceiptLong as ReceiptIcon,
  Search as SearchIcon,
  Star as StarIcon,
} from '@mui/icons-material'
import { useCurrentRide, useRequestRide } from '@/hooks/useRides'
import { Location as RideLocation, Ride, RideRequest, RideStatus } from '@/types'

type RideStage = 'searching' | 'matched' | 'en_route' | 'arrived' | 'trip' | 'completed'
type MapPoint = RideLocation & { id: string; type: 'pickup' | 'dropoff' | 'driver' | 'nearby'; label: string }

const locationPresets: RideLocation[] = [
  { latitude: 40.7527, longitude: -73.9772, address: 'Grand Central Terminal, New York, NY' },
  { latitude: 40.7580, longitude: -73.9855, address: 'Times Square, New York, NY' },
  { latitude: 40.7536, longitude: -73.9832, address: 'Bryant Park, New York, NY' },
  { latitude: 40.7484, longitude: -73.9857, address: 'Empire State Building, New York, NY' },
  { latitude: 40.7505, longitude: -73.9934, address: 'Penn Station, New York, NY' },
  { latitude: 40.7605, longitude: -73.9743, address: 'Rockefeller Center, New York, NY' },
  { latitude: 40.7061, longitude: -74.0086, address: 'Wall Street, New York, NY' },
  { latitude: 40.7127, longitude: -74.0134, address: 'One World Trade Center, New York, NY' },
]

const timelineSteps: Array<{ key: RideStatus; label: string }> = [
  { key: 'requested', label: 'Searching' },
  { key: 'driver_assigned', label: 'Matched' },
  { key: 'driver_arriving', label: 'En route' },
  { key: 'driver_arrived', label: 'Arrived' },
  { key: 'trip_started', label: 'In trip' },
  { key: 'completed', label: 'Complete' },
]

const driverProfile = {
  name: 'Marcus Chen',
  rating: 4.92,
  vehicle: 'Toyota Camry Hybrid',
  plate: 'NYC-4821',
  avatar: 'MC',
}

const round = (value: number, places = 2) => {
  const factor = Math.pow(10, places)
  return Math.round(value * factor) / factor
}

const toRadians = (value: number) => value * Math.PI / 180

const calculateDistance = (pickup: RideLocation, dropoff: RideLocation) => {
  const radiusKm = 6371
  const dLat = toRadians(dropoff.latitude - pickup.latitude)
  const dLon = toRadians(dropoff.longitude - pickup.longitude)
  const pickupLat = toRadians(pickup.latitude)
  const dropoffLat = toRadians(dropoff.latitude)
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
    + Math.cos(pickupLat) * Math.cos(dropoffLat)
    * Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return radiusKm * c
}

const estimateRide = (pickup: RideLocation, dropoff: RideLocation) => {
  const distance = calculateDistance(pickup, dropoff)
  const etaMinutes = Math.max(4, Math.ceil(distance * 4 + 3))
  const estimatedFare = Math.max(6, 3.5 + (distance * 1.45) + (etaMinutes * 0.4))

  return {
    pickup_location: pickup,
    dropoff_location: dropoff,
    distance: round(distance),
    estimatedFare: round(estimatedFare),
    etaMinutes,
    etaSeconds: etaMinutes * 60,
  }
}

const getStage = (status?: RideStatus): RideStage => {
  if (status === 'completed') return 'completed'
  if (status === 'trip_started' || status === 'in_progress') return 'trip'
  if (status === 'driver_arrived') return 'arrived'
  if (status === 'driver_arriving') return 'en_route'
  if (status === 'driver_assigned' || status === 'driver_accepted') return 'matched'
  return 'searching'
}

const getTimelineProgress = (status?: RideStatus) => {
  if (status === 'driver_accepted') status = 'driver_assigned'
  if (status === 'in_progress') status = 'trip_started'
  const index = timelineSteps.findIndex((step) => step.key === status)
  if (index < 0) return status === 'cancelled' ? 0 : 12
  return Math.round(((index + 1) / timelineSteps.length) * 100)
}

const interpolateLocation = (from: RideLocation, to: RideLocation, ratio: number, address: string): RideLocation => ({
  latitude: round(from.latitude + ((to.latitude - from.latitude) * ratio), 6),
  longitude: round(from.longitude + ((to.longitude - from.longitude) * ratio), 6),
  address,
})

const buildDriverOrigin = (pickup: RideLocation): RideLocation => ({
  latitude: round(pickup.latitude + 0.012, 6),
  longitude: round(pickup.longitude - 0.016, 6),
  address: 'Nearby driver location',
})

const getDriverLocation = (pickup: RideLocation, dropoff: RideLocation, stage: RideStage): RideLocation => {
  const origin = buildDriverOrigin(pickup)
  if (stage === 'en_route') return interpolateLocation(origin, pickup, 0.68, 'Driver approaching pickup')
  if (stage === 'arrived') return { ...pickup, address: 'Driver at pickup' }
  if (stage === 'trip') return interpolateLocation(pickup, dropoff, 0.46, 'Trip in progress')
  if (stage === 'completed') return { ...dropoff, address: 'Trip completed' }
  return origin
}

const getNearbyDrivers = (pickup: RideLocation): MapPoint[] => {
  const offsets = [
    { lat: 0.006, lon: -0.012 },
    { lat: -0.009, lon: 0.007 },
    { lat: 0.014, lon: 0.009 },
    { lat: -0.013, lon: -0.01 },
  ]

  return offsets.map((offset, index) => ({
    id: `nearby-${index}`,
    type: 'nearby',
    label: `Driver ${index + 1}`,
    latitude: round(pickup.latitude + offset.lat, 6),
    longitude: round(pickup.longitude + offset.lon, 6),
    address: 'Nearby driver',
  }))
}

const getPointStyle = (point: RideLocation, allPoints: RideLocation[]) => {
  const latitudes = allPoints.map((item) => item.latitude)
  const longitudes = allPoints.map((item) => item.longitude)
  let minLat = Math.min(...latitudes)
  let maxLat = Math.max(...latitudes)
  let minLon = Math.min(...longitudes)
  let maxLon = Math.max(...longitudes)
  const latPadding = Math.max((maxLat - minLat) * 0.28, 0.01)
  const lonPadding = Math.max((maxLon - minLon) * 0.28, 0.01)
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

const getStageCopy = (ride: Ride | null, stage: RideStage, estimate: ReturnType<typeof estimateRide>) => {
  const eta = Math.max(1, Math.round((ride?.eta_seconds || estimate.etaSeconds) / 60))
  const remainingDistance = ride ? ride.distance : estimate.distance

  switch (stage) {
    case 'searching':
      return { title: 'Searching for Driver', detail: 'Nearby drivers are being matched', metric: `${eta} min pickup area` }
    case 'matched':
      return { title: 'Driver Matched', detail: `${ride?.driver_name || driverProfile.name} is reviewing your pickup`, metric: `${Math.max(2, eta - 2)} min away` }
    case 'en_route':
      return { title: `Arriving in ${Math.max(1, eta - 1)} minutes`, detail: 'Driver is on the way to pickup', metric: `${round(remainingDistance / 3, 1)} km to pickup` }
    case 'arrived':
      return { title: 'Driver Arrived', detail: 'Meet your driver at the pickup point', metric: 'Pickup highlighted' }
    case 'trip':
      return { title: `${Math.max(3, eta - 3)} minutes remaining`, detail: 'Trip is in progress', metric: `${round(Math.max(0.4, remainingDistance * 0.55), 1)} km left` }
    case 'completed':
      return { title: 'Trip Completed', detail: 'Receipt and ride details are ready', metric: `$${(ride?.actual_fare || ride?.estimated_fare || estimate.estimatedFare).toFixed(2)}` }
    default:
      return { title: 'Ride Tracking', detail: 'Live ride updates', metric: `${eta} min` }
  }
}

const darkFieldStyles = {
  '& .MuiOutlinedInput-root': {
    color: '#111827',
    borderRadius: 2,
    backgroundColor: '#FFFFFF',
    '& fieldset': { borderColor: '#D7DEE8' },
    '&:hover fieldset': { borderColor: '#94A3B8' },
    '&.Mui-focused fieldset': { borderColor: '#111827', boxShadow: '0 0 0 3px rgba(17, 24, 39, 0.08)' },
  },
  '& .MuiInputLabel-root': { color: '#64748B' },
}

const RideMap: React.FC<{
  pickup: RideLocation
  dropoff: RideLocation
  stage: RideStage
  activeRide: Ride | null
}> = ({ pickup, dropoff, stage, activeRide }) => {
  const driverLocation = getDriverLocation(pickup, dropoff, stage)
  const nearbyDrivers = stage === 'searching' ? getNearbyDrivers(pickup) : []
  const routeStart = stage === 'matched' || stage === 'en_route' ? driverLocation : pickup
  const routeEnd = stage === 'matched' || stage === 'en_route' ? pickup : dropoff

  const points: MapPoint[] = [
    { ...pickup, id: 'pickup', type: 'pickup', label: 'Pickup' },
    { ...dropoff, id: 'dropoff', type: 'dropoff', label: 'Dropoff' },
    ...(stage !== 'searching' ? [{ ...driverLocation, id: 'driver', type: 'driver' as const, label: activeRide?.driver_name || driverProfile.name }] : []),
    ...nearbyDrivers,
  ]
  const allLocations = [...points, routeStart, routeEnd]
  const routeStartStyle = getPointStyle(routeStart, allLocations)
  const routeEndStyle = getPointStyle(routeEnd, allLocations)

  return (
    <Box sx={{
      position: 'relative',
      minHeight: { xs: 330, md: 520 },
      overflow: 'hidden',
      background:
        'linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(241, 245, 249, 0.94)), repeating-linear-gradient(0deg, transparent 0 42px, rgba(15, 23, 42, 0.06) 42px 43px), repeating-linear-gradient(90deg, transparent 0 42px, rgba(15, 23, 42, 0.06) 42px 43px)',
      borderRadius: { xs: 0, md: 2 },
      border: { xs: 'none', md: '1px solid rgba(15, 23, 42, 0.08)' },
      boxShadow: { xs: 'none', md: '0 24px 70px rgba(15, 23, 42, 0.12)' },
    }}>
      <Box sx={{
        position: 'absolute',
        inset: 0,
        background:
          'linear-gradient(28deg, transparent 30%, rgba(59, 130, 246, 0.11) 31%, rgba(59, 130, 246, 0.11) 34%, transparent 35%), linear-gradient(118deg, transparent 48%, rgba(17, 24, 39, 0.09) 49%, rgba(17, 24, 39, 0.09) 51%, transparent 52%)',
      }} />
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'visible' }}>
        <line
          x1={routeStartStyle.left}
          y1={routeStartStyle.top}
          x2={routeEndStyle.left}
          y2={routeEndStyle.top}
          stroke={stage === 'searching' ? 'rgba(15, 23, 42, 0.18)' : '#111827'}
          strokeWidth="5"
          strokeLinecap="round"
          strokeDasharray={stage === 'matched' || stage === 'en_route' ? '10 12' : '0'}
        />
        <line
          x1={getPointStyle(pickup, allLocations).left}
          y1={getPointStyle(pickup, allLocations).top}
          x2={getPointStyle(dropoff, allLocations).left}
          y2={getPointStyle(dropoff, allLocations).top}
          stroke="rgba(34, 197, 94, 0.55)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray={stage === 'trip' || stage === 'completed' ? '0' : '6 10'}
        />
      </svg>

      {points.map((point) => {
        const style = getPointStyle(point, allLocations)
        const isDriver = point.type === 'driver' || point.type === 'nearby'
        return (
          <Box
            key={point.id}
            sx={{
              position: 'absolute',
              transform: 'translate(-50%, -50%)',
              ...style,
              zIndex: isDriver ? 4 : 5,
            }}
          >
            <Box sx={{
              width: point.type === 'nearby' ? 34 : 42,
              height: point.type === 'nearby' ? 34 : 42,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              color: point.type === 'dropoff' ? '#FFFFFF' : '#111827',
              background: point.type === 'dropoff'
                ? '#111827'
                : point.type === 'driver'
                  ? '#FFFFFF'
                  : point.type === 'nearby'
                    ? '#FFFFFF'
                    : '#22C55E',
              border: point.type === 'nearby' ? '2px solid rgba(17, 24, 39, 0.16)' : '3px solid #FFFFFF',
              boxShadow: '0 12px 28px rgba(15, 23, 42, 0.25)',
              animation: point.type === 'nearby' ? 'riderPulse 1.8s ease-in-out infinite' : 'none',
            }}>
              {point.type === 'pickup' && <PickupIcon fontSize="small" />}
              {point.type === 'dropoff' && <FlagIcon fontSize="small" />}
              {(point.type === 'driver' || point.type === 'nearby') && <CarIcon fontSize="small" />}
            </Box>
          </Box>
        )
      })}

      <Box sx={{
        position: 'absolute',
        left: 16,
        right: 16,
        top: 16,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
      }}>
        <Chip
          icon={stage === 'searching' ? <SearchIcon /> : <NavigationIcon />}
          label={stage === 'searching' ? 'Matching nearby drivers' : 'Live route'}
          sx={{ bgcolor: '#FFFFFF', color: '#111827', fontWeight: 800, boxShadow: '0 12px 28px rgba(15,23,42,0.12)' }}
        />
        <Chip
          label={`${pickup.latitude.toFixed(4)}, ${pickup.longitude.toFixed(4)}`}
          sx={{ display: { xs: 'none', sm: 'inline-flex' }, bgcolor: 'rgba(255,255,255,0.92)', color: '#475569', fontWeight: 700 }}
        />
      </Box>
    </Box>
  )
}

const TripTimeline: React.FC<{ status: RideStatus }> = ({ status }) => {
  const normalizedStatus = status === 'driver_accepted'
    ? 'driver_assigned'
    : status === 'in_progress'
      ? 'trip_started'
      : status
  const activeIndex = timelineSteps.findIndex((step) => step.key === normalizedStatus)

  return (
    <Stack spacing={1.15}>
      {timelineSteps.map((step, index) => {
        const completed = activeIndex >= index
        const active = activeIndex === index
        return (
          <Box key={step.key} sx={{ display: 'flex', alignItems: 'center', gap: 1.2 }}>
            {completed
              ? <CheckIcon sx={{ color: '#16A34A', fontSize: 19 }} />
              : <PendingIcon sx={{ color: '#CBD5E1', fontSize: 19 }} />}
            <Typography sx={{ fontSize: 13, fontWeight: active ? 900 : 700, color: completed ? '#111827' : '#94A3B8' }}>
              {step.label}
            </Typography>
          </Box>
        )
      })}
    </Stack>
  )
}

export const HomePage: React.FC = () => {
  const [pickupLocation, setPickupLocation] = useState<RideLocation>(locationPresets[0])
  const [dropoffLocation, setDropoffLocation] = useState<RideLocation>(locationPresets[1])
  const [rideRequestDialog, setRideRequestDialog] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [locationError, setLocationError] = useState('')

  const { data: currentRide } = useCurrentRide()
  const requestRideMutation = useRequestRide()

  const activePickup = currentRide?.pickup_location || pickupLocation
  const activeDropoff = currentRide?.dropoff_location || dropoffLocation
  const rideEstimate = useMemo(() => estimateRide(activePickup, activeDropoff), [activePickup, activeDropoff])
  const requestEstimate = useMemo(() => estimateRide(pickupLocation, dropoffLocation), [pickupLocation, dropoffLocation])
  const currentStatus = currentRide?.status || 'requested'
  const stage = getStage(currentRide?.status)
  const progress = getTimelineProgress(currentStatus)
  const stageCopy = getStageCopy(currentRide || null, stage, rideEstimate)
  const fare = currentRide?.actual_fare || currentRide?.estimated_fare || requestEstimate.estimatedFare
  const vehicle = currentRide?.vehicle || (currentRide?.vehicle_details
    ? `${currentRide.vehicle_details.make} ${currentRide.vehicle_details.model}`
    : driverProfile.vehicle)
  const plate = currentRide?.license_plate || currentRide?.vehicle_details?.license_plate || driverProfile.plate
  const driverName = currentRide?.driver_name || currentRide?.driver?.name || driverProfile.name
  const driverRating = currentRide?.driver_rating || currentRide?.driver?.rating || driverProfile.rating

  const selectPreset = (address: string, type: 'pickup' | 'dropoff') => {
    const nextLocation = locationPresets.find((location) => location.address === address)
    if (!nextLocation) return
    if (type === 'pickup') setPickupLocation(nextLocation)
    else setDropoffLocation(nextLocation)
  }

  const useCurrentLocation = () => {
    setLocationError('')
    if (!navigator.geolocation) {
      setLocationError('Location access is unavailable in this browser.')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setPickupLocation({
          latitude: round(position.coords.latitude, 6),
          longitude: round(position.coords.longitude, 6),
          address: 'Current location',
        })
      },
      () => setLocationError('Unable to access current location.'),
      { enableHighAccuracy: true, timeout: 8000 }
    )
  }

  const handleRequestRide = () => {
    const rideRequest: RideRequest = {
      pickup_location: pickupLocation,
      dropoff_location: dropoffLocation,
    }
    requestRideMutation.mutate(rideRequest, {
      onSuccess: (ride) => {
        const nextFare = ride.estimated_fare ?? requestEstimate.estimatedFare
        const etaSeconds = ride.eta_seconds ?? requestEstimate.etaSeconds
        setSuccessMessage(`Ride requested. Estimated fare $${nextFare.toFixed(2)}. Pickup ETA ${Math.round(etaSeconds / 60)} minutes.`)
        setRideRequestDialog(false)
        setTimeout(() => setSuccessMessage(''), 5000)
      },
    })
  }

  return (
    <Box sx={{
      minHeight: '100vh',
      pb: 10,
      background: '#F3F6FA',
      color: '#111827',
      '@keyframes riderPulse': {
        '0%, 100%': { transform: 'scale(1)', opacity: 1 },
        '50%': { transform: 'scale(1.08)', opacity: 0.72 },
      },
    }}>
      <Box sx={{
        maxWidth: 1180,
        mx: 'auto',
        p: { xs: 0, md: 2.5 },
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', lg: '1.35fr 0.9fr' },
        gap: { xs: 0, lg: 2.5 },
      }}>
        <RideMap pickup={activePickup} dropoff={activeDropoff} stage={stage} activeRide={currentRide || null} />

        <Box sx={{
          p: { xs: 2, md: 0 },
          mt: { xs: -5, md: 0 },
          position: 'relative',
          zIndex: 10,
        }}>
          <Card sx={{
            borderRadius: 2,
            boxShadow: '0 24px 70px rgba(15, 23, 42, 0.16)',
            border: '1px solid rgba(15, 23, 42, 0.08)',
            overflow: 'hidden',
          }}>
            <CardContent sx={{ p: 2.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mb: 2 }}>
                <Avatar sx={{ bgcolor: '#111827', width: 46, height: 46 }}>
                  {stage === 'searching' ? <SearchIcon /> : <CarIcon />}
                </Avatar>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="h6" sx={{ fontWeight: 900, letterSpacing: 0 }}>
                    {stageCopy.title}
                  </Typography>
                  <Typography sx={{ color: '#64748B', fontWeight: 600, fontSize: 14 }}>
                    {stageCopy.detail}
                  </Typography>
                </Box>
                <Chip label={stageCopy.metric} sx={{ bgcolor: '#ECFDF3', color: '#166534', fontWeight: 900 }} />
              </Box>

              <LinearProgress
                variant="determinate"
                value={progress}
                sx={{
                  height: 8,
                  borderRadius: 8,
                  mb: 2.25,
                  bgcolor: '#E2E8F0',
                  '& .MuiLinearProgress-bar': { bgcolor: '#111827', borderRadius: 8 },
                }}
              />

              <TripTimeline status={currentStatus} />
            </CardContent>
          </Card>

          {currentRide && stage !== 'searching' && stage !== 'completed' && (
            <Card sx={{ mt: 2, borderRadius: 2, boxShadow: '0 16px 45px rgba(15,23,42,0.10)', border: '1px solid rgba(15,23,42,0.08)' }}>
              <CardContent sx={{ p: 2.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Avatar sx={{ width: 56, height: 56, bgcolor: '#0F172A', fontWeight: 900 }}>
                    {driverName.split(' ').map((part) => part[0]).join('').slice(0, 2)}
                  </Avatar>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography sx={{ fontWeight: 900, fontSize: 17 }}>{driverName}</Typography>
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ color: '#64748B', flexWrap: 'wrap' }}>
                      <StarIcon sx={{ color: '#F59E0B', fontSize: 18 }} />
                      <Typography sx={{ fontSize: 14, fontWeight: 800 }}>{driverRating.toFixed(2)}</Typography>
                      <Typography sx={{ fontSize: 14 }}>{vehicle}</Typography>
                    </Stack>
                  </Box>
                  <Chip label={plate} sx={{ bgcolor: '#111827', color: '#FFFFFF', fontWeight: 900, letterSpacing: 0.5 }} />
                </Box>
              </CardContent>
            </Card>
          )}

          {currentRide && stage === 'completed' && (
            <Card sx={{ mt: 2, borderRadius: 2, boxShadow: '0 16px 45px rgba(15,23,42,0.10)', border: '1px solid rgba(15,23,42,0.08)' }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                  <ReceiptIcon sx={{ color: '#111827' }} />
                  <Typography sx={{ fontWeight: 900, fontSize: 18 }}>Trip Summary</Typography>
                </Stack>
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5 }}>
                  <Box>
                    <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800 }}>Fare</Typography>
                    <Typography sx={{ fontWeight: 900 }}>${fare.toFixed(2)}</Typography>
                  </Box>
                  <Box>
                    <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800 }}>Distance</Typography>
                    <Typography sx={{ fontWeight: 900 }}>{currentRide.distance.toFixed(1)} km</Typography>
                  </Box>
                  <Box>
                    <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800 }}>Duration</Typography>
                    <Typography sx={{ fontWeight: 900 }}>{Math.max(5, Math.round((currentRide.eta_seconds || rideEstimate.etaSeconds) / 60))} min</Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          )}

          <Card sx={{ mt: 2, borderRadius: 2, boxShadow: '0 16px 45px rgba(15,23,42,0.10)', border: '1px solid rgba(15,23,42,0.08)' }}>
            <CardContent sx={{ p: 2.5 }}>
              <Typography sx={{ fontWeight: 900, fontSize: 20, mb: 2 }}>Where to?</Typography>
              <Stack spacing={2}>
                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 1 }}>
                  <TextField
                    select
                    fullWidth
                    label="Pickup"
                    value={pickupLocation.address}
                    onChange={(event) => selectPreset(event.target.value, 'pickup')}
                    sx={darkFieldStyles}
                  >
                    {[pickupLocation, ...locationPresets.filter((location) => location.address !== pickupLocation.address)]
                      .map((location) => (
                        <MenuItem key={location.address} value={location.address}>
                          {location.address}
                        </MenuItem>
                      ))}
                  </TextField>
                  <IconButton
                    aria-label="Use current location"
                    onClick={useCurrentLocation}
                    sx={{ width: 56, height: 56, bgcolor: '#111827', color: '#FFFFFF', '&:hover': { bgcolor: '#0F172A' } }}
                  >
                    <MyLocationIcon />
                  </IconButton>
                </Box>

                <TextField
                  select
                  fullWidth
                  label="Destination"
                  value={dropoffLocation.address}
                  onChange={(event) => selectPreset(event.target.value, 'dropoff')}
                  sx={darkFieldStyles}
                >
                  {locationPresets.map((location) => (
                    <MenuItem key={location.address} value={location.address}>
                      {location.address}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>

              <Box sx={{ mt: 2, p: 2, borderRadius: 2, bgcolor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                  <Typography sx={{ color: '#64748B', fontWeight: 800, fontSize: 13 }}>Estimated fare</Typography>
                  <Typography sx={{ fontWeight: 900, fontSize: 20 }}>${requestEstimate.estimatedFare.toFixed(2)}</Typography>
                </Stack>
                <Stack direction="row" spacing={2} sx={{ color: '#475569' }}>
                  <Stack direction="row" spacing={0.6} alignItems="center">
                    <NavigationIcon sx={{ fontSize: 17 }} />
                    <Typography sx={{ fontSize: 13, fontWeight: 700 }}>{requestEstimate.distance.toFixed(2)} km</Typography>
                  </Stack>
                  <Stack direction="row" spacing={0.6} alignItems="center">
                    <TimeIcon sx={{ fontSize: 17 }} />
                    <Typography sx={{ fontSize: 13, fontWeight: 700 }}>{requestEstimate.etaMinutes} min</Typography>
                  </Stack>
                </Stack>
              </Box>

              <Button
                fullWidth
                variant="contained"
                onClick={() => setRideRequestDialog(true)}
                disabled={requestRideMutation.isPending || pickupLocation.address === dropoffLocation.address}
                sx={{
                  mt: 2,
                  py: 1.35,
                  bgcolor: '#111827',
                  color: '#FFFFFF',
                  fontWeight: 900,
                  borderRadius: 2,
                  textTransform: 'none',
                  '&:hover': { bgcolor: '#020617', transform: 'translateY(-1px)' },
                }}
              >
                {requestRideMutation.isPending ? <CircularProgress size={20} sx={{ color: 'inherit' }} /> : 'Request Ride'}
              </Button>
            </CardContent>
          </Card>

          {(successMessage || locationError || requestRideMutation.error) && (
            <Alert
              severity={successMessage ? 'success' : 'error'}
              sx={{ mt: 2, borderRadius: 2, boxShadow: '0 12px 32px rgba(15,23,42,0.10)' }}
            >
              {successMessage || locationError || 'Failed to request ride'}
            </Alert>
          )}
        </Box>
      </Box>

      <Dialog
        open={rideRequestDialog}
        onClose={() => setRideRequestDialog(false)}
        PaperProps={{ sx: { borderRadius: 2, width: '100%', maxWidth: 460 } }}
      >
        <DialogTitle sx={{ fontWeight: 900 }}>Confirm Ride</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Box>
              <Typography sx={{ color: '#64748B', fontWeight: 800, fontSize: 13 }}>Pickup</Typography>
              <Typography sx={{ fontWeight: 800 }}>{pickupLocation.address}</Typography>
              <Typography sx={{ color: '#94A3B8', fontSize: 12 }}>
                {pickupLocation.latitude.toFixed(6)}, {pickupLocation.longitude.toFixed(6)}
              </Typography>
            </Box>
            <Divider />
            <Box>
              <Typography sx={{ color: '#64748B', fontWeight: 800, fontSize: 13 }}>Destination</Typography>
              <Typography sx={{ fontWeight: 800 }}>{dropoffLocation.address}</Typography>
              <Typography sx={{ color: '#94A3B8', fontSize: 12 }}>
                {dropoffLocation.latitude.toFixed(6)}, {dropoffLocation.longitude.toFixed(6)}
              </Typography>
            </Box>
            <Box sx={{ p: 2, borderRadius: 2, bgcolor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
              <Typography sx={{ color: '#64748B', fontWeight: 800, fontSize: 13 }}>Estimated trip</Typography>
              <Typography sx={{ fontWeight: 900, fontSize: 22 }}>${requestEstimate.estimatedFare.toFixed(2)}</Typography>
              <Typography sx={{ color: '#64748B', fontWeight: 700 }}>
                {requestEstimate.distance.toFixed(2)} km | {requestEstimate.etaMinutes} min
              </Typography>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button onClick={() => setRideRequestDialog(false)} sx={{ color: '#64748B', fontWeight: 800 }}>Cancel</Button>
          <Button onClick={handleRequestRide} variant="contained" sx={{ bgcolor: '#111827', fontWeight: 900, borderRadius: 2 }}>
            Confirm Ride
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
