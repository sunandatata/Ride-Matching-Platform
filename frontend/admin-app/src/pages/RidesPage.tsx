import React, { useState } from 'react'
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Typography,
  Chip,
  Card,
  CardContent,
  Grid,
} from '@mui/material'
import {
  AttachMoney as RevenueIcon,
  CheckCircle as CompletedIcon,
  DirectionsCar as ActiveIcon,
  LocalTaxi as TotalIcon,
} from '@mui/icons-material'
import { useRides, useCancelRide } from '@/hooks/useRides'
import { DataTable } from '@/components/DataTable'
import { MetricCard } from '@/components/MetricCard'
import { Ride } from '@/types'
import dayjs from 'dayjs'

export const RidesPage: React.FC = () => {
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [selectedRide, setSelectedRide] = useState<Ride | null>(null)
  const [cancelDialog, setCancelDialog] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [detailsDialog, setDetailsDialog] = useState(false)

  const { data, isLoading } = useRides({
    page: page + 1,
    per_page: rowsPerPage,
  })
  const cancelMutation = useCancelRide()
  const rides = data?.data || []
  const activeRides = rides.filter((ride) => !['completed', 'cancelled'].includes(ride.status)).length
  const completedRides = rides.filter((ride) => ride.status === 'completed').length
  const pageRevenue = rides.reduce((sum, ride) => sum + (ride.actual_fare || ride.estimated_fare || 0), 0)

  const handleCancel = () => {
    if (selectedRide) {
      cancelMutation.mutate({ rideId: selectedRide.id, reason: cancelReason })
      setCancelDialog(false)
      setSelectedRide(null)
      setCancelReason('')
    }
  }

  const getStatusColor = (status: Ride['status']) => {
    switch (status) {
      case 'completed':
        return 'success'
      case 'cancelled':
        return 'error'
      case 'in_progress':
      case 'trip_started':
        return 'info'
      case 'driver_assigned':
      case 'driver_accepted':
      case 'driver_arriving':
      case 'driver_arrived':
        return 'warning'
      default:
        return 'default'
    }
  }

  const columns = [
    {
      id: 'id',
      label: 'Ride ID',
      minWidth: 150,
      searchAccessor: (row: Ride) => row.id,
      render: (row: Ride) => row.id.slice(0, 8),
    },
    {
      id: 'pickup_location',
      label: 'Pickup',
      minWidth: 200,
      searchAccessor: (row: Ride) => row.pickup_location.address,
      sortAccessor: (row: Ride) => row.pickup_location.address,
      render: (row: Ride) => row.pickup_location.address,
    },
    {
      id: 'dropoff_location',
      label: 'Dropoff',
      minWidth: 200,
      searchAccessor: (row: Ride) => row.dropoff_location.address,
      sortAccessor: (row: Ride) => row.dropoff_location.address,
      render: (row: Ride) => row.dropoff_location.address,
    },
    {
      id: 'status',
      label: 'Status',
      minWidth: 120,
      sortAccessor: (row: Ride) => row.status,
      render: (row: Ride) => (
        <Chip label={row.status.replace('_', ' ')} color={getStatusColor(row.status)} size="small" />
      ),
    },
    {
      id: 'distance',
      label: 'Distance (km)',
      minWidth: 100,
      sortAccessor: (row: Ride) => row.distance,
      render: (row: Ride) => row.distance.toFixed(2),
    },
    {
      id: 'fare',
      label: 'Fare',
      minWidth: 100,
      sortAccessor: (row: Ride) => row.actual_fare || row.estimated_fare,
      render: (row: Ride) => `$${(row.actual_fare || row.estimated_fare).toFixed(2)}`,
    },
    {
      id: 'rating',
      label: 'Rating',
      minWidth: 80,
      sortAccessor: (row: Ride) => row.rating || 0,
      render: (row: Ride) => row.rating ? `${row.rating}/5` : 'N/A',
    },
    {
      id: 'created_at',
      label: 'Created',
      minWidth: 150,
      sortAccessor: (row: Ride) => new Date(row.created_at).getTime(),
      render: (row: Ride) => dayjs(row.created_at).format('MMM DD, HH:mm'),
    },
    {
      id: 'actions',
      label: 'Actions',
      minWidth: 150,
      sortable: false,
      render: (row: Ride) => (
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            size="small"
            variant="text"
            onClick={() => {
              setSelectedRide(row)
              setDetailsDialog(true)
            }}
          >
            View
          </Button>
          {['requested', 'driver_assigned', 'driver_accepted', 'driver_arriving', 'driver_arrived'].includes(row.status) && (
            <Button
              size="small"
              variant="contained"
              color="error"
              onClick={() => {
                setSelectedRide(row)
                setCancelDialog(true)
              }}
            >
              Cancel
            </Button>
          )}
        </Box>
      ),
    },
  ]

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
          Trip Management
        </Typography>
        <Typography sx={{ color: '#64748B', mt: 0.5 }}>
          Monitor active rides, resolve exceptions, and inspect trip details.
        </Typography>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Total Rides" value={data?.total || rides.length} icon={<TotalIcon />} tone="blue" isLoading={isLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Active Rides" value={activeRides} icon={<ActiveIcon />} tone="amber" isLoading={isLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Completed" value={completedRides} icon={<CompletedIcon />} tone="green" isLoading={isLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Page Revenue" value={`$${pageRevenue.toFixed(2)}`} icon={<RevenueIcon />} tone="violet" isLoading={isLoading} />
        </Grid>
      </Grid>

      <Card sx={{ borderRadius: 2, boxShadow: '0 12px 30px rgba(15, 23, 42, 0.08)', border: '1px solid #E2E8F0' }}>
        <CardContent sx={{ p: 0 }}>
          <DataTable
            columns={columns}
            rows={rides}
            isLoading={isLoading}
            page={page}
            rowsPerPage={rowsPerPage}
            total={data?.total || 0}
            onPageChange={setPage}
            onRowsPerPageChange={setRowsPerPage}
            searchPlaceholder="Search rides by ID, pickup, or destination"
            filters={[
              {
                id: 'status',
                label: 'Status',
                options: [
                  { value: 'requested', label: 'Requested' },
                  { value: 'driver_assigned', label: 'Assigned' },
                  { value: 'driver_accepted', label: 'Accepted' },
                  { value: 'driver_arriving', label: 'Arriving' },
                  { value: 'driver_arrived', label: 'Arrived' },
                  { value: 'trip_started', label: 'Started' },
                  { value: 'in_progress', label: 'In Progress' },
                  { value: 'completed', label: 'Completed' },
                  { value: 'cancelled', label: 'Cancelled' },
                ],
                getValue: (row: Ride) => row.status,
              },
            ]}
          />
        </CardContent>
      </Card>

      <Dialog open={detailsDialog} onClose={() => setDetailsDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Ride Details</DialogTitle>
        <DialogContent>
          {selectedRide && (
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <Typography variant="body2" color="textSecondary">
                  Ride ID
                </Typography>
                <Typography variant="body1">{selectedRide.id}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="body2" color="textSecondary">
                  Pickup Location
                </Typography>
                <Typography variant="body1">{selectedRide.pickup_location.address}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="body2" color="textSecondary">
                  Dropoff Location
                </Typography>
                <Typography variant="body1">{selectedRide.dropoff_location.address}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="body2" color="textSecondary">
                  Distance
                </Typography>
                <Typography variant="body1">{selectedRide.distance.toFixed(2)} km</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="body2" color="textSecondary">
                  Fare
                </Typography>
                <Typography variant="body1">
                  ${(selectedRide.actual_fare || selectedRide.estimated_fare).toFixed(2)}
                </Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="body2" color="textSecondary">
                  Status
                </Typography>
                <Chip label={selectedRide.status} color={getStatusColor(selectedRide.status)} />
              </Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={cancelDialog} onClose={() => setCancelDialog(false)}>
        <DialogTitle>Cancel Ride</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Reason"
            multiline
            rows={4}
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
            margin="normal"
            placeholder="Enter reason for cancellation"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelDialog(false)}>Cancel</Button>
          <Button
            onClick={handleCancel}
            variant="contained"
            color="error"
            disabled={!cancelReason.trim()}
          >
            Cancel Ride
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
