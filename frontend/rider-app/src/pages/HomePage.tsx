import React, { useEffect, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Alert,
  Avatar,
  Box,
  Autocomplete,
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
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import {
  Call as CallIcon,
  CallEnd as CallEndIcon,
  AccessTime as TimeIcon,
  CheckCircle as CheckIcon,
  DirectionsCar as CarIcon,
  Flag as FlagIcon,
  GraphicEq as SoundIcon,
  ChatBubbleOutline as MessageIcon,
  PhoneInTalk as PhoneIcon,
  MyLocation as MyLocationIcon,
  Navigation as NavigationIcon,
  PersonPinCircle as PickupIcon,
  RadioButtonUnchecked as PendingIcon,
  ReceiptLong as ReceiptIcon,
  Search as SearchIcon,
  Star as StarIcon,
  Share as ShareIcon,
  Close as CloseIcon,
  LocalTaxi as TaxiIcon,
  Timer as TimerIcon,
} from '@mui/icons-material'
import { useCurrentRide, useRequestRide } from '@/hooks/useRides'
import { Location as RideLocation, Ride, RideRequest, RideStatus } from '@/types'
import { locationSearchService } from '@/services/locationSearchService'

type MapPoint = RideLocation & { id: string; type: 'pickup' | 'dropoff' | 'driver' | 'nearby'; label: string }

const timelineSteps: Array<{ key: VisualRideStage; label: string }> = [
  { key: 'searching', label: 'Searching for drivers' },
  { key: 'matched', label: 'Driver matched' },
  { key: 'en_route', label: 'Driver is on the way' },
  { key: 'arrived', label: 'Driver arrived' },
  { key: 'trip_started', label: 'Trip started' },
  { key: 'trip_in_progress', label: 'Trip in progress' },
  { key: 'arriving_destination', label: 'Arriving at destination' },
  { key: 'completed', label: 'Trip completed' },
]

const driverProfile = {
  name: 'Marcus Chen',
  rating: 4.92,
  vehicle: 'Toyota Camry Hybrid',
  plate: 'RIDE-4821',
  avatar: 'MC',
  tripsCompleted: 1284,
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

const getTimelineProgress = (stage: VisualRideStage) => {
  const index = timelineSteps.findIndex((step) => step.key === stage)
  if (index < 0) return 0
  return Math.round(((index + 1) / timelineSteps.length) * 100)
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

const getStageCopy = (ride: Ride | null, stage: VisualRideStage, estimate: ReturnType<typeof estimateRide>) => {
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
    case 'trip_started':
      return { title: 'Trip Started', detail: 'You are in the car and heading to the destination', metric: `${Math.max(3, eta - 3)} min remaining` }
    case 'trip_in_progress':
      return { title: `${Math.max(3, eta - 3)} minutes remaining`, detail: 'Trip is in progress', metric: `${round(Math.max(0.4, remainingDistance * 0.55), 1)} km left` }
    case 'arriving_destination':
      return { title: 'Arriving at Destination', detail: 'Final approach to your dropoff', metric: `${Math.max(1, eta)} min remaining` }
    case 'completed':
      return { title: 'Trip Completed', detail: 'Receipt and ride details are ready', metric: `$${(ride?.actual_fare || ride?.estimated_fare || estimate.estimatedFare).toFixed(2)}` }
    default:
      return { title: 'Ride Tracking', detail: 'Live ride updates', metric: `${eta} min` }
  }
}

type VisualRideStage =
  | 'searching'
  | 'matched'
  | 'en_route'
  | 'arrived'
  | 'trip_started'
  | 'trip_in_progress'
  | 'arriving_destination'
  | 'completed'

type ToastItem = {
  id: string
  message: string
  severity: 'info' | 'success'
}

type RoutePoint = {
  latitude: number
  longitude: number
  address: string
  id: string
  type: 'origin' | 'pickup' | 'mid' | 'dropoff'
}

type MotionState = {
  stage: VisualRideStage
  progress: number
  etaMinutes: number
  distanceRemainingKm: number
  driverMinutesAway: number
  currentPoint: { latitude: number; longitude: number }
  rotation: number
  pulsePickup: boolean
  cameraScale: number
  statusText: string
}

const visualStages: VisualRideStage[] = [
  'searching',
  'matched',
  'en_route',
  'arrived',
  'trip_started',
  'trip_in_progress',
  'arriving_destination',
  'completed',
]

const backendStageOrder: Record<RideStatus, number> = {
  requested: 0,
  driver_accepted: 1,
  driver_assigned: 1,
  driver_arriving: 2,
  driver_arrived: 3,
  trip_started: 4,
  in_progress: 5,
  completed: 7,
  cancelled: 0,
}

const backendVisualStage = (status?: RideStatus | null): VisualRideStage => {
  switch (status) {
    case 'driver_assigned':
    case 'driver_accepted':
      return 'matched'
    case 'driver_arriving':
      return 'en_route'
    case 'driver_arrived':
      return 'arrived'
    case 'trip_started':
      return 'trip_started'
    case 'in_progress':
      return 'trip_in_progress'
    case 'completed':
      return 'completed'
    case 'requested':
    case 'cancelled':
    default:
      return 'searching'
  }
}

const stageWindowSeconds: Record<VisualRideStage, number> = {
  searching: 6,
  matched: 7,
  en_route: 12,
  arrived: 4,
  trip_started: 4,
  trip_in_progress: 28,
  arriving_destination: 10,
  completed: 1,
}

const getVisualStageFromElapsed = (elapsedSeconds: number): VisualRideStage => {
  if (elapsedSeconds < 6) return 'searching'
  if (elapsedSeconds < 13) return 'matched'
  if (elapsedSeconds < 25) return 'en_route'
  if (elapsedSeconds < 30) return 'arrived'
  if (elapsedSeconds < 34) return 'trip_started'
  if (elapsedSeconds < 62) return 'trip_in_progress'
  if (elapsedSeconds < 72) return 'arriving_destination'
  return 'completed'
}

const getVisualStage = (ride: Ride | null, now: number): VisualRideStage => {
  if (!ride) return 'searching'
  if (ride.status === 'cancelled') return 'searching'
  if (ride.status === 'completed') return 'completed'

  const startedAt = new Date(ride.created_at).getTime()
  const elapsedSeconds = Number.isFinite(startedAt) ? Math.max(0, Math.floor((now - startedAt) / 1000)) : 0
  const simulatedStage = getVisualStageFromElapsed(elapsedSeconds)
  const backendRank = backendStageOrder[ride.status] ?? 0
  const backendStage = backendVisualStage(ride.status)
  const simulatedRank = visualStages.indexOf(simulatedStage)

  return backendRank > simulatedRank ? backendStage : simulatedStage
}

const getStageProgress = (ride: Ride | null, now: number, stage: VisualRideStage) => {
  if (!ride) return 0
  const startedAt = new Date(ride.created_at).getTime()
  const elapsedSeconds = Number.isFinite(startedAt) ? Math.max(0, Math.floor((now - startedAt) / 1000)) : 0
  const previousStages = visualStages.slice(0, visualStages.indexOf(stage))
  const elapsedBeforeStage = previousStages.reduce((sum, key) => sum + stageWindowSeconds[key], 0)
  const window = Math.max(1, stageWindowSeconds[stage])
  return Math.min(1, Math.max(0, (elapsedSeconds - elapsedBeforeStage) / window))
}

const buildRouteTrack = (pickup: RideLocation, dropoff: RideLocation): RoutePoint[] => {
  const latitudeDelta = dropoff.latitude - pickup.latitude
  const longitudeDelta = dropoff.longitude - pickup.longitude
  const origin: RoutePoint = {
    id: 'origin',
    type: 'origin',
    latitude: pickup.latitude + (latitudeDelta * 0.07) + 0.015,
    longitude: pickup.longitude - 0.018,
    address: 'Driver origin',
  }
  const viaOne: RoutePoint = {
    id: 'via-1',
    type: 'mid',
    latitude: pickup.latitude + (latitudeDelta * 0.28) - 0.018,
    longitude: pickup.longitude + (longitudeDelta * 0.18) + 0.013,
    address: 'Route bend',
  }
  const viaTwo: RoutePoint = {
    id: 'via-2',
    type: 'mid',
    latitude: pickup.latitude + (latitudeDelta * 0.66) + 0.012,
    longitude: pickup.longitude + (longitudeDelta * 0.72) - 0.015,
    address: 'Traffic section',
  }

  return [
    origin,
    { ...pickup, id: 'pickup', type: 'pickup' },
    viaOne,
    viaTwo,
    { ...dropoff, id: 'dropoff', type: 'dropoff' },
  ]
}

const pointToScreen = (point: RoutePoint, allPoints: RoutePoint[]) => {
  const latitudes = allPoints.map((item) => item.latitude)
  const longitudes = allPoints.map((item) => item.longitude)
  let minLat = Math.min(...latitudes)
  let maxLat = Math.max(...latitudes)
  let minLon = Math.min(...longitudes)
  let maxLon = Math.max(...longitudes)

  const latPadding = Math.max((maxLat - minLat) * 0.3, 0.015)
  const lonPadding = Math.max((maxLon - minLon) * 0.3, 0.015)
  minLat -= latPadding
  maxLat += latPadding
  minLon -= lonPadding
  maxLon += lonPadding

  const x = ((point.longitude - minLon) / (maxLon - minLon)) * 100
  const y = (1 - ((point.latitude - minLat) / (maxLat - minLat))) * 100

  return {
    left: `${Math.min(90, Math.max(8, x))}%`,
    top: `${Math.min(88, Math.max(12, y))}%`,
  }
}

const interpolateRoute = (points: RoutePoint[], progress: number) => {
  const screenPoints = points.map((point) => {
    const position = pointToScreen(point, points)
    return {
      x: Number.parseFloat(position.left),
      y: Number.parseFloat(position.top),
      point,
    }
  })

  const segments = screenPoints.slice(1).map((entry, index) => {
    const from = screenPoints[index]
    const to = entry
    const dx = to.x - from.x
    const dy = to.y - from.y
    const length = Math.max(0.001, Math.hypot(dx, dy))
    return { from, to, dx, dy, length }
  })

  const totalLength = segments.reduce((sum, segment) => sum + segment.length, 0)
  let target = totalLength * progress
  let current = segments[0]
  for (const segment of segments) {
    if (target <= segment.length) {
      current = segment
      break
    }
    target -= segment.length
    current = segment
  }

  const ratio = current.length === 0 ? 0 : Math.min(1, Math.max(0, target / current.length))
  const x = current.from.x + (current.dx * ratio)
  const y = current.from.y + (current.dy * ratio)
  const angle = Math.atan2(current.dy, current.dx) * (180 / Math.PI)

  return { x, y, angle, screenPoints }
}

const getMotionState = (ride: Ride | null, now: number, pickup: RideLocation, dropoff: RideLocation): MotionState => {
  const stage = getVisualStage(ride, now)
  const stageProgress = getStageProgress(ride, now, stage)
  const activeRide = ride || null
  const routeProgressRanges: Record<VisualRideStage, [number, number]> = {
    searching: [0.07, 0.24],
    matched: [0.24, 0.42],
    en_route: [0.42, 0.5],
    arrived: [0.5, 0.5],
    trip_started: [0.5, 0.56],
    trip_in_progress: [0.56, 0.92],
    arriving_destination: [0.92, 0.99],
    completed: [1, 1],
  }

  const [start, end] = routeProgressRanges[stage]
  const routeProgress = start + ((end - start) * stageProgress)
  const routeTrack = buildRouteTrack(pickup, dropoff)
  const point = interpolateRoute(routeTrack, routeProgress)
  const routeDistance = activeRide?.distance || calculateDistance(pickup, dropoff)
  const driverMinutesAway = stage === 'searching'
    ? Math.max(1, Math.round(7 - (stageProgress * 3)))
    : stage === 'matched'
      ? Math.max(1, Math.round(6 - (stageProgress * 2)))
      : stage === 'en_route'
        ? Math.max(1, Math.round(4 - (stageProgress * 2)))
        : stage === 'arrived'
          ? 0
          : Math.max(0, Math.round((1 - routeProgress) * 18))

  const etaMinutes = stage === 'completed'
    ? 0
    : stage === 'arrived'
      ? 0
      : Math.max(1, Math.round(stage === 'trip_in_progress' || stage === 'arriving_destination'
        ? routeDistance * (1 - routeProgress) * 3.2
        : driverMinutesAway))

  const distanceRemainingKm = stage === 'completed'
    ? 0
    : Math.max(0.1, round(routeDistance * (1 - routeProgress), 1))

  const statusText = (() => {
    switch (stage) {
      case 'searching': return 'Searching for drivers...'
      case 'matched': return `Driver matched`
      case 'en_route': return `Driver is ${Math.max(1, driverMinutesAway)} min away`
      case 'arrived': return 'Driver arriving'
      case 'trip_started': return 'Trip started'
      case 'trip_in_progress': return 'Trip in progress'
      case 'arriving_destination': return 'Arriving at destination'
      case 'completed': return 'Trip completed'
      default: return 'Ride tracking'
    }
  })()

  return {
    stage,
    progress: routeProgress,
    etaMinutes,
    distanceRemainingKm,
    driverMinutesAway,
    currentPoint: { latitude: point.y, longitude: point.x },
    rotation: point.angle,
    pulsePickup: stage === 'arrived' || stage === 'trip_started',
    cameraScale: stage === 'searching' ? 1.02 : stage === 'matched' ? 1.05 : stage === 'en_route' ? 1.08 : stage === 'trip_in_progress' ? 1.12 : 1.06,
    statusText,
  }
}

const playTone = (frequency: number, duration = 180, type: OscillatorType = 'sine', gainValue = 0.03) => {
  if (typeof window === 'undefined') return
  const AudioCtor = window.AudioContext || (window as Window & typeof globalThis & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!AudioCtor) return
  const context = new AudioCtor()
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.type = type
  oscillator.frequency.value = frequency
  gain.gain.value = gainValue
  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start()
  window.setTimeout(() => {
    oscillator.stop()
    context.close().catch(() => undefined)
  }, duration)
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

const LocationSearchField: React.FC<{
  label: string
  placeholder: string
  value: RideLocation
  inputValue: string
  options: RideLocation[]
  loading: boolean
  onInputChange: (value: string) => void
  onChange: (value: RideLocation) => void
}> = ({ label, placeholder, value, inputValue, options, loading, onInputChange, onChange }) => {
  return (
    <Autocomplete
      value={value}
      inputValue={inputValue}
      options={options}
      loading={loading}
      onInputChange={(_, nextValue) => onInputChange(nextValue)}
      onChange={(_, nextValue) => {
        if (nextValue) onChange(nextValue)
      }}
      isOptionEqualToValue={(option, current) => (
        option.latitude === current.latitude
        && option.longitude === current.longitude
        && option.address === current.address
      )}
      getOptionLabel={(option) => option.address}
      filterOptions={(availableOptions) => availableOptions}
      renderInput={(params) => (
        <TextField
          {...params}
          fullWidth
          label={label}
          placeholder={placeholder}
          sx={darkFieldStyles}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading ? <CircularProgress color="inherit" size={18} sx={{ mr: 1 }} /> : null}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
      renderOption={(props, option) => (
        <li {...props} key={`${option.address}-${option.latitude}-${option.longitude}`}>
          <Box>
            <Typography sx={{ fontWeight: 800, color: '#111827' }}>{option.address}</Typography>
            <Typography sx={{ fontSize: 12, color: '#64748B' }}>
              {option.latitude.toFixed(4)}, {option.longitude.toFixed(4)}
            </Typography>
          </Box>
        </li>
      )}
      noOptionsText="No places found"
    />
  )
}

const RideMap: React.FC<{
  pickup: RideLocation
  dropoff: RideLocation
  stage: VisualRideStage
  activeRide: Ride | null
  motionState: MotionState
}> = ({ pickup, dropoff, stage, activeRide, motionState }) => {
  const routeTrack = useMemo(() => buildRouteTrack(pickup, dropoff), [pickup, dropoff])
  const interpolated = useMemo(() => interpolateRoute(routeTrack, motionState.progress), [routeTrack, motionState.progress])
  const nearbyDrivers = stage === 'searching' ? getNearbyDrivers(pickup) : []
  const lineSegments = interpolated.screenPoints

  return (
    <Box sx={{
      position: 'relative',
      minHeight: { xs: 360, md: 560 },
      overflow: 'hidden',
      background:
        'radial-gradient(circle at 20% 20%, rgba(34, 197, 94, 0.18), transparent 28%), radial-gradient(circle at 76% 28%, rgba(59, 130, 246, 0.16), transparent 25%), linear-gradient(135deg, rgba(244, 247, 251, 0.98), rgba(232, 240, 250, 0.95))',
      borderRadius: { xs: 0, md: 3 },
      border: { xs: 'none', md: '1px solid rgba(15, 23, 42, 0.08)' },
      boxShadow: { xs: 'none', md: '0 24px 70px rgba(15, 23, 42, 0.12)' },
    }}>
      <motion.div
        animate={{ scale: motionState.cameraScale }}
        transition={{ type: 'spring', stiffness: 100, damping: 18 }}
        style={{
          position: 'absolute',
          inset: '-6%',
          transformOrigin: '50% 50%',
          background:
            'repeating-linear-gradient(0deg, transparent 0 41px, rgba(15, 23, 42, 0.06) 41px 42px), repeating-linear-gradient(90deg, transparent 0 41px, rgba(15, 23, 42, 0.06) 41px 42px)',
        }}
      />

      <Box sx={{
        position: 'absolute',
        inset: 0,
        background:
          'linear-gradient(28deg, transparent 30%, rgba(148, 163, 184, 0.09) 31%, rgba(148, 163, 184, 0.09) 33%, transparent 34%), linear-gradient(118deg, transparent 46%, rgba(15, 23, 42, 0.08) 47%, rgba(15, 23, 42, 0.08) 49%, transparent 50%)',
      }} />

      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}>
        <defs>
          <linearGradient id="routeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#111827" />
            <stop offset="40%" stopColor="#22C55E" />
            <stop offset="72%" stopColor="#F59E0B" />
            <stop offset="100%" stopColor="#EF4444" />
          </linearGradient>
          <linearGradient id="movingGradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#FFFFFF" />
            <stop offset="100%" stopColor="#93C5FD" />
          </linearGradient>
        </defs>
        {lineSegments.slice(0, -1).map((segment, index) => {
          const next = lineSegments[index + 1]
          const trafficColor = index === 0 ? '#94A3B8' : index === 1 ? '#22C55E' : index === 2 ? '#F59E0B' : '#EF4444'
          const isActive = motionState.progress * (lineSegments.length - 1) >= index
          return (
            <line
              key={`${segment.point.id}-${next.point.id}`}
              x1={`${segment.x}%`}
              y1={`${segment.y}%`}
              x2={`${next.x}%`}
              y2={`${next.y}%`}
              stroke={trafficColor}
              strokeWidth={isActive ? 8 : 6}
              strokeLinecap="round"
              strokeDasharray={index === 0 && stage === 'searching' ? '9 10' : '0'}
              opacity={isActive ? 0.9 : 0.4}
            />
          )
        })}

        <line
          x1={`${lineSegments[0].x}%`}
          y1={`${lineSegments[0].y}%`}
          x2={`${interpolated.x}%`}
          y2={`${interpolated.y}%`}
          stroke="url(#movingGradient)"
          strokeWidth="4"
          strokeLinecap="round"
          strokeDasharray="8 10"
          opacity="0.82"
        />
      </svg>

      {nearbyDrivers.map((driver, index) => {
        const style = pointToScreen({ ...driver, id: driver.id, type: 'mid' }, routeTrack)
        return (
          <motion.div
            key={driver.id}
            animate={{ y: [0, -6, 0], x: [0, index % 2 === 0 ? 4 : -4, 0], opacity: [0.65, 1, 0.7] }}
            transition={{ duration: 2.8 + (index * 0.3), repeat: Infinity, ease: 'easeInOut' }}
            style={{
              position: 'absolute',
              left: style.left,
              top: style.top,
              transform: 'translate(-50%, -50%)',
              zIndex: 3,
            }}
          >
            <Box sx={{
              width: 32,
              height: 32,
              borderRadius: '50%',
              bgcolor: '#FFFFFF',
              border: '2px solid rgba(15, 23, 42, 0.16)',
              boxShadow: '0 10px 24px rgba(15, 23, 42, 0.12)',
              display: 'grid',
              placeItems: 'center',
              color: '#111827',
            }}>
              <TaxiIcon sx={{ fontSize: 18 }} />
            </Box>
          </motion.div>
        )
      })}

      <AnimatePresence>
        {motionState.pulsePickup && (
          <motion.div
            key="pickup-pulse"
            initial={{ opacity: 0.1, scale: 0.85 }}
            animate={{ opacity: [0.1, 0.35, 0.1], scale: [0.85, 1.15, 0.95] }}
            exit={{ opacity: 0 }}
            transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
            style={{
              position: 'absolute',
              left: pointToScreen(routeTrack[1], routeTrack).left,
              top: pointToScreen(routeTrack[1], routeTrack).top,
              width: 120,
              height: 120,
              borderRadius: '50%',
              transform: 'translate(-50%, -50%)',
              border: '2px solid rgba(34, 197, 94, 0.35)',
              background: 'radial-gradient(circle, rgba(34, 197, 94, 0.14), transparent 66%)',
              zIndex: 2,
            }}
          />
        )}
      </AnimatePresence>

      <motion.div
        animate={{
          left: `${interpolated.x}%`,
          top: `${interpolated.y}%`,
          rotate: `${interpolated.angle}deg`,
        }}
        transition={{ type: 'spring', stiffness: 140, damping: 20 }}
        style={{
          position: 'absolute',
          transform: 'translate(-50%, -50%)',
          zIndex: 6,
        }}
      >
        <Box sx={{
          width: 54,
          height: 54,
          borderRadius: '18px',
          bgcolor: '#111827',
          color: '#FFFFFF',
          display: 'grid',
          placeItems: 'center',
          boxShadow: '0 18px 34px rgba(15, 23, 42, 0.28)',
          border: '3px solid rgba(255,255,255,0.9)',
        }}>
          <CarIcon sx={{ fontSize: 28 }} />
        </Box>
      </motion.div>

      {routeTrack.map((point) => {
        const style = pointToScreen(point, routeTrack)
        const isPickup = point.type === 'pickup'
        const isDropoff = point.type === 'dropoff'
        return (
          <Box
            key={point.id}
            sx={{
              position: 'absolute',
              left: style.left,
              top: style.top,
              transform: 'translate(-50%, -50%)',
              zIndex: isPickup || isDropoff ? 5 : 4,
            }}
          >
            <Box sx={{
              width: isPickup || isDropoff ? 46 : 32,
              height: isPickup || isDropoff ? 46 : 32,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              color: isDropoff ? '#FFFFFF' : '#111827',
              background: isDropoff ? '#111827' : isPickup ? '#22C55E' : '#FFFFFF',
              border: '3px solid rgba(255,255,255,0.9)',
              boxShadow: '0 14px 30px rgba(15, 23, 42, 0.22)',
            }}>
              {isPickup && <PickupIcon fontSize="small" />}
              {isDropoff && <FlagIcon fontSize="small" />}
              {point.type === 'mid' && <NavigationIcon sx={{ fontSize: 18 }} />}
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
          label={motionState.statusText}
          sx={{ bgcolor: '#FFFFFF', color: '#111827', fontWeight: 900, boxShadow: '0 12px 28px rgba(15,23,42,0.12)' }}
        />
        <Chip
          label={`${motionState.etaMinutes} min • ${motionState.distanceRemainingKm.toFixed(1)} km left`}
          sx={{ display: { xs: 'none', sm: 'inline-flex' }, bgcolor: 'rgba(255,255,255,0.94)', color: '#475569', fontWeight: 800 }}
        />
      </Box>

      <Box sx={{
        position: 'absolute',
        right: 16,
        bottom: 16,
        bgcolor: 'rgba(255,255,255,0.92)',
        border: '1px solid rgba(15, 23, 42, 0.08)',
        borderRadius: 2,
        px: 1.5,
        py: 1,
        boxShadow: '0 12px 28px rgba(15,23,42,0.10)',
        maxWidth: 240,
      }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
          <SoundIcon sx={{ fontSize: 17, color: '#16A34A' }} />
          <Typography sx={{ fontSize: 12, fontWeight: 900, color: '#111827' }}>Live ride updates</Typography>
        </Stack>
        <Typography sx={{ fontSize: 12, color: '#475569', lineHeight: 1.35 }}>
          {activeRide?.driver_name || driverProfile.name} is moving smoothly toward your pickup and route progress updates every second.
        </Typography>
      </Box>
    </Box>
  )
}

const TripTimeline: React.FC<{ stage: VisualRideStage }> = ({ stage }) => {
  const activeIndex = timelineSteps.findIndex((step) => step.key === stage)

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
  const fallbackLocations = locationSearchService.getFallbackLocations()
  const [pickupLocation, setPickupLocation] = useState<RideLocation>(fallbackLocations[0])
  const [dropoffLocation, setDropoffLocation] = useState<RideLocation>(fallbackLocations[1])
  const [pickupQuery, setPickupQuery] = useState(fallbackLocations[0].address)
  const [dropoffQuery, setDropoffQuery] = useState(fallbackLocations[1].address)
  const [pickupSuggestions, setPickupSuggestions] = useState<RideLocation[]>(fallbackLocations)
  const [dropoffSuggestions, setDropoffSuggestions] = useState<RideLocation[]>(fallbackLocations)
  const [pickupLoading, setPickupLoading] = useState(false)
  const [dropoffLoading, setDropoffLoading] = useState(false)
  const [rideRequestDialog, setRideRequestDialog] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [locationError, setLocationError] = useState('')
  const [now, setNow] = useState(Date.now())
  const [callOpen, setCallOpen] = useState(false)
  const [callConnected, setCallConnected] = useState(false)
  const [callDuration, setCallDuration] = useState(0)
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const stageRef = useRef<VisualRideStage>('searching')
  const callHandledRef = useRef(false)
  const toastSeedRef = useRef(0)

  const { data: currentRide } = useCurrentRide()
  const requestRideMutation = useRequestRide()

  const activePickup = currentRide?.pickup_location || pickupLocation
  const activeDropoff = currentRide?.dropoff_location || dropoffLocation
  const rideEstimate = useMemo(() => estimateRide(activePickup, activeDropoff), [activePickup, activeDropoff])
  const requestEstimate = useMemo(() => estimateRide(pickupLocation, dropoffLocation), [pickupLocation, dropoffLocation])
  const stage = useMemo(() => getVisualStage(currentRide || null, now), [currentRide, now])
  const motionState = useMemo(() => getMotionState(currentRide || null, now, activePickup, activeDropoff), [currentRide, now, activePickup, activeDropoff])
  const progress = getTimelineProgress(stage)
  const stageCopy = getStageCopy(currentRide || null, stage, rideEstimate)
  const fare = currentRide?.actual_fare || currentRide?.estimated_fare || requestEstimate.estimatedFare
  const vehicle = currentRide?.vehicle || (currentRide?.vehicle_details
    ? `${currentRide.vehicle_details.make} ${currentRide.vehicle_details.model}`
    : driverProfile.vehicle)
  const plate = currentRide?.license_plate || currentRide?.vehicle_details?.license_plate || driverProfile.plate
  const driverName = currentRide?.driver_name || currentRide?.driver?.name || driverProfile.name
  const driverRating = currentRide?.driver_rating || currentRide?.driver?.rating || driverProfile.rating

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const previousStage = stageRef.current
    if (previousStage !== stage) {
      if (stage === 'matched' && !callHandledRef.current) {
        setCallOpen(true)
        playTone(660, 220, 'triangle', 0.025)
        pushToast('Driver accepted your ride', 'info')
        callHandledRef.current = true
      }
      if (stage === 'en_route') {
        pushToast(`Driver is ${motionState.driverMinutesAway} minutes away`, 'info')
      }
      if (stage === 'arrived') {
        pushToast('Driver has arrived', 'success')
        playTone(520, 260, 'sine', 0.03)
      }
      if (stage === 'trip_started') {
        pushToast('Trip started', 'success')
        playTone(784, 180, 'triangle', 0.03)
      }
      if (stage === 'completed') {
        setCallOpen(false)
        setCallConnected(false)
        setCallDuration(0)
        pushToast('Trip completed', 'success')
        playTone(988, 240, 'sine', 0.04)
      }
    }
    stageRef.current = stage
  }, [motionState.driverMinutesAway, stage])

  useEffect(() => {
    if (!callConnected) return
    const timer = window.setInterval(() => setCallDuration((seconds) => seconds + 1), 1000)
    return () => window.clearInterval(timer)
  }, [callConnected])

  useEffect(() => {
    callHandledRef.current = false
    setCallOpen(false)
    setCallConnected(false)
    setCallDuration(0)
  }, [currentRide?.id])

  useEffect(() => {
    let cancelled = false
    const query = pickupQuery.trim()
    const timer = window.setTimeout(async () => {
      if (!query) {
        setPickupSuggestions(locationSearchService.getFallbackLocations())
        setPickupLoading(false)
        return
      }
      setPickupLoading(true)
      try {
        const results = await locationSearchService.searchLocations(query)
        if (!cancelled) setPickupSuggestions(results)
      } finally {
        if (!cancelled) setPickupLoading(false)
      }
    }, 300)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [pickupQuery])

  useEffect(() => {
    let cancelled = false
    const query = dropoffQuery.trim()
    const timer = window.setTimeout(async () => {
      if (!query) {
        setDropoffSuggestions(locationSearchService.getFallbackLocations())
        setDropoffLoading(false)
        return
      }
      setDropoffLoading(true)
      try {
        const results = await locationSearchService.searchLocations(query)
        if (!cancelled) setDropoffSuggestions(results)
      } finally {
        if (!cancelled) setDropoffLoading(false)
      }
    }, 300)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [dropoffQuery])

  const useCurrentLocation = () => {
    setLocationError('')
    if (!navigator.geolocation) {
      setLocationError('Location access is unavailable in this browser.')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const nextLocation = {
          latitude: round(position.coords.latitude, 6),
          longitude: round(position.coords.longitude, 6),
          address: 'Current location',
        }
        setPickupLocation(nextLocation)
        setPickupQuery(nextLocation.address)
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

  const pushToast = (message: string, severity: ToastItem['severity'] = 'info') => {
    const id = `toast-${toastSeedRef.current++}`
    setToasts((items) => [...items, { id, message, severity }])
    window.setTimeout(() => {
      setToasts((items) => items.filter((item) => item.id !== id))
    }, 4200)
  }

  const handleCallDriver = () => {
    setCallOpen(true)
    pushToast('Incoming driver call', 'info')
    playTone(660, 160, 'triangle', 0.03)
  }

  const handleAcceptCall = () => {
    setCallOpen(false)
    setCallConnected(true)
    pushToast('Call connected', 'success')
    playTone(880, 140, 'sine', 0.03)
  }

  const handleDeclineCall = () => {
    setCallOpen(false)
    setCallConnected(false)
    pushToast('Call declined', 'info')
    playTone(280, 180, 'sawtooth', 0.02)
  }

  const handleEndCall = () => {
    setCallOpen(false)
    setCallConnected(false)
    setCallDuration(0)
    pushToast('Call ended', 'info')
  }

  const handleMessageDriver = () => {
    pushToast('Message sent to driver', 'info')
  }

  const handleShareTrip = async () => {
    const tripSummary = `${driverName} • ${activePickup.address} to ${activeDropoff.address}`
    try {
      if (navigator.share) {
        await navigator.share({ title: 'Ride Share', text: tripSummary })
      } else if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(tripSummary)
        pushToast('Trip details copied', 'success')
        return
      }
      pushToast('Trip shared', 'success')
    } catch {
      pushToast('Unable to share trip', 'info')
    }
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
        <RideMap pickup={activePickup} dropoff={activeDropoff} stage={stage} activeRide={currentRide || null} motionState={motionState} />

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

              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 0.9fr' }, gap: 1.5, mb: 2 }}>
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                  <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800, mb: 0.5 }}>Live status</Typography>
                  <Typography sx={{ fontWeight: 900, fontSize: 16 }}>{motionState.statusText}</Typography>
                  <Typography sx={{ color: '#475569', fontSize: 13, mt: 0.5 }}>
                    {motionState.etaMinutes > 0 ? `Driver is ${motionState.etaMinutes} min away` : 'Driver is at your location'}
                  </Typography>
                </Box>
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                  <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800, mb: 0.5 }}>Distance remaining</Typography>
                  <Typography sx={{ fontWeight: 900, fontSize: 16 }}>{motionState.distanceRemainingKm.toFixed(1)} km</Typography>
                  <Typography sx={{ color: '#475569', fontSize: 13, mt: 0.5 }}>
                    Progress {Math.round(motionState.progress * 100)}%
                  </Typography>
                </Box>
              </Box>

              <TripTimeline stage={stage} />
            </CardContent>
          </Card>

          {currentRide && stage !== 'searching' && stage !== 'completed' && (
            <Card sx={{ mt: 2, borderRadius: 2, boxShadow: '0 16px 45px rgba(15,23,42,0.10)', border: '1px solid rgba(15,23,42,0.08)' }}>
              <CardContent sx={{ p: 2.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Avatar sx={{
                    width: 56,
                    height: 56,
                    bgcolor: '#0F172A',
                    fontWeight: 900,
                    background: 'linear-gradient(135deg, #111827, #334155)',
                  }}>
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
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.25, mt: 2 }}>
                  <Button onClick={handleCallDriver} variant="outlined" startIcon={<CallIcon />} sx={{ textTransform: 'none', fontWeight: 900, borderRadius: 2 }}>
                    Call Driver
                  </Button>
                  <Button onClick={handleMessageDriver} variant="outlined" startIcon={<MessageIcon />} sx={{ textTransform: 'none', fontWeight: 900, borderRadius: 2 }}>
                    Message Driver
                  </Button>
                  <Button onClick={handleShareTrip} variant="outlined" startIcon={<ShareIcon />} sx={{ textTransform: 'none', fontWeight: 900, borderRadius: 2 }}>
                    Share Trip
                  </Button>
                </Box>
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5, mt: 2 }}>
                  <Box>
                    <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800 }}>Trips completed</Typography>
                    <Typography sx={{ fontWeight: 900 }}>{driverProfile.tripsCompleted.toLocaleString()}</Typography>
                  </Box>
                  <Box>
                    <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800 }}>ETA</Typography>
                    <Typography sx={{ fontWeight: 900 }}>{motionState.etaMinutes} min</Typography>
                  </Box>
                  <Box>
                    <Typography sx={{ color: '#64748B', fontSize: 12, fontWeight: 800 }}>Call</Typography>
                    <Typography sx={{ fontWeight: 900 }}>{callConnected ? `Connected ${callDuration}s` : 'Ready'}</Typography>
                  </Box>
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
                  <LocationSearchField
                    label="Pickup"
                    placeholder="Search any pickup location"
                    value={pickupLocation}
                    inputValue={pickupQuery}
                    options={pickupSuggestions}
                    loading={pickupLoading}
                    onInputChange={setPickupQuery}
                    onChange={(location) => {
                      setPickupLocation(location)
                      setPickupQuery(location.address)
                    }}
                  />
                  <IconButton
                    aria-label="Use current location"
                    onClick={useCurrentLocation}
                    sx={{ width: 56, height: 56, bgcolor: '#111827', color: '#FFFFFF', '&:hover': { bgcolor: '#0F172A' } }}
                  >
                    <MyLocationIcon />
                  </IconButton>
                </Box>

                <LocationSearchField
                  label="Destination"
                  placeholder="Search any destination worldwide"
                  value={dropoffLocation}
                  inputValue={dropoffQuery}
                  options={dropoffSuggestions}
                  loading={dropoffLoading}
                  onInputChange={setDropoffQuery}
                  onChange={(location) => {
                    setDropoffLocation(location)
                    setDropoffQuery(location.address)
                  }}
                />
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

      <Box sx={{
        position: 'fixed',
        right: { xs: 16, md: 24 },
        bottom: { xs: 16, md: 24 },
        zIndex: 1400,
      }}>
        <motion.div
          animate={{ y: [0, -4, 0], scale: callOpen ? 1.06 : 1 }}
          transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
        >
          <IconButton
            onClick={handleCallDriver}
            sx={{
              width: 58,
              height: 58,
              bgcolor: '#111827',
              color: '#FFFFFF',
              boxShadow: '0 18px 36px rgba(15, 23, 42, 0.32)',
              '&:hover': { bgcolor: '#020617' },
            }}
          >
            {callOpen || callConnected ? <PhoneIcon /> : <CallIcon />}
          </IconButton>
        </motion.div>
      </Box>

      <Box sx={{
        position: 'fixed',
        top: 16,
        right: 16,
        zIndex: 1500,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        width: { xs: 'calc(100vw - 32px)', sm: 360 },
        pointerEvents: 'none',
      }}>
        <AnimatePresence>
          {toasts.slice(-4).map((toast) => (
            <motion.div
              key={toast.id}
              initial={{ opacity: 0, y: -14, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.96 }}
              transition={{ duration: 0.25 }}
            >
              <Alert
                severity={toast.severity}
                sx={{
                  borderRadius: 2,
                  boxShadow: '0 18px 34px rgba(15,23,42,0.16)',
                  pointerEvents: 'auto',
                }}
                action={
                  <IconButton size="small" onClick={() => setToasts((items) => items.filter((item) => item.id !== toast.id))}>
                    <CloseIcon fontSize="small" />
                  </IconButton>
                }
              >
                {toast.message}
              </Alert>
            </motion.div>
          ))}
        </AnimatePresence>
      </Box>

      <Dialog
        open={callOpen}
        onClose={handleDeclineCall}
        PaperProps={{
          sx: {
            borderRadius: 3,
            width: '100%',
            maxWidth: 420,
            overflow: 'hidden',
            bgcolor: '#0B1220',
            color: '#FFFFFF',
          },
        }}
      >
        <DialogTitle sx={{ pb: 1.5, fontWeight: 900, color: '#FFFFFF' }}>
          Demo Driver is calling...
        </DialogTitle>
        <DialogContent sx={{ pt: 1 }}>
          <Stack spacing={2} alignItems="center">
            <Box sx={{
              width: 112,
              height: 112,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              background: 'linear-gradient(135deg, #111827, #334155)',
              border: '3px solid rgba(255,255,255,0.18)',
              boxShadow: '0 22px 50px rgba(15,23,42,0.35)',
            }}>
              <Avatar sx={{ width: 88, height: 88, bgcolor: 'rgba(255,255,255,0.12)', color: '#FFFFFF', fontWeight: 900, fontSize: 28 }}>
                {driverProfile.avatar}
              </Avatar>
            </Box>
            <Box sx={{ textAlign: 'center' }}>
              <Typography sx={{ fontSize: 20, fontWeight: 900 }}>{driverName}</Typography>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="center" sx={{ color: 'rgba(255,255,255,0.76)', mt: 0.5 }}>
                <TimerIcon sx={{ fontSize: 18 }} />
                <Typography sx={{ fontSize: 13, fontWeight: 700 }}>
                  {callConnected ? `Connected • ${callDuration}s` : 'Incoming voice call'}
                </Typography>
              </Stack>
            </Box>
            <Box sx={{ width: '100%', p: 1.5, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.06)' }}>
              <Typography sx={{ color: 'rgba(255,255,255,0.75)', fontSize: 12, fontWeight: 800 }}>Driver details</Typography>
              <Typography sx={{ fontWeight: 800, mt: 0.5 }}>{vehicle}</Typography>
              <Typography sx={{ color: 'rgba(255,255,255,0.72)', fontSize: 13 }}>
                Rating ★ {driverRating.toFixed(2)} • {plate}
              </Typography>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5, gap: 1.5 }}>
          <Button
            fullWidth
            variant="contained"
            onClick={handleDeclineCall}
            startIcon={<CallEndIcon />}
            sx={{ bgcolor: '#991B1B', fontWeight: 900, borderRadius: 2, textTransform: 'none', '&:hover': { bgcolor: '#7F1D1D' } }}
          >
            Decline
          </Button>
          <Button
            fullWidth
            variant="contained"
            onClick={handleAcceptCall}
            startIcon={<CallIcon />}
            sx={{ bgcolor: '#16A34A', fontWeight: 900, borderRadius: 2, textTransform: 'none', '&:hover': { bgcolor: '#15803D' } }}
          >
            Accept
          </Button>
          {callConnected && (
            <Button
              fullWidth
              variant="outlined"
              onClick={handleEndCall}
              sx={{ color: '#FFFFFF', borderColor: 'rgba(255,255,255,0.24)', fontWeight: 800, borderRadius: 2, textTransform: 'none' }}
            >
              End Call
            </Button>
          )}
        </DialogActions>
      </Dialog>

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
