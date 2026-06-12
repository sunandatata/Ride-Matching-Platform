import React, { useState } from 'react'
import {
  Box,
  Typography,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Rating,
  CircularProgress,
} from '@mui/material'
import { LocationOn as LocationIcon } from '@mui/icons-material'
import { useRideHistory, useRateRide } from '@/hooks/useRides'
import { Ride } from '@/types'
import dayjs from 'dayjs'

export const RideHistoryPage: React.FC = () => {
  const [page, setPage] = useState(1)
  const [selectedRide, setSelectedRide] = useState<Ride | null>(null)
  const [ratingDialog, setRatingDialog] = useState(false)
  const [rating, setRating] = useState(5)
  const [feedback, setFeedback] = useState('')

  const { data, isLoading } = useRideHistory(page)
  const rateRideMutation = useRateRide()

  const handleRate = () => {
    if (selectedRide) {
      rateRideMutation.mutate(
        { rideId: selectedRide.id, rating, feedback },
        { onSuccess: () => { setRatingDialog(false); setSelectedRide(null); setRating(5); setFeedback('') } }
      )
    }
  }

  if (isLoading) return <Box sx={{ p: 2, pb: 10 }}><CircularProgress /></Box>

  return (
    <Box sx={{ p: 2, pb: 10 }}>
      <Typography variant="h5" sx={{ fontWeight: 'bold', mb: 3 }}>Ride History</Typography>

      {data?.data?.length === 0 ? (
        <Typography color="textSecondary">No rides yet</Typography>
      ) : (
        <Box>
          {data?.data?.map((ride) => (
            <Card key={ride.id} sx={{ mb: 2, cursor: 'pointer' }}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                  <Box>
                    <Typography variant="body2" color="textSecondary">{dayjs(ride.created_at).format('MMM DD, HH:mm')}</Typography>
                    <Chip label={ride.status} size="small" color={ride.status === 'completed' ? 'success' : 'default'} />
                  </Box>
                  <Typography variant="h6">${(ride.actual_fare || ride.estimated_fare).toFixed(2)}</Typography>
                </Box>

                <Box sx={{ display: 'flex', alignItems: 'flex-start', mb: 1 }}>
                  <LocationIcon sx={{ mr: 1, mt: 0.5, color: '#1976d2', fontSize: 18 }} />
                  <Typography variant="body2">{ride.pickup_location.address}</Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
                  <LocationIcon sx={{ mr: 1, mt: 0.5, color: '#4caf50', fontSize: 18 }} />
                  <Typography variant="body2">{ride.dropoff_location.address}</Typography>
                </Box>

                {ride.rating && <Box sx={{ mt: 2 }}><Rating value={ride.rating} readOnly size="small" /></Box>}

                {ride.status === 'completed' && !ride.rating && (
                  <Button size="small" onClick={() => { setSelectedRide(ride); setRatingDialog(true) }} sx={{ mt: 2 }}>
                    Rate Ride
                  </Button>
                )}
              </CardContent>
            </Card>
          ))}

          {data && data.total > (page * 10) && (
            <Button fullWidth onClick={() => setPage(page + 1)} sx={{ mt: 2 }}>Load More</Button>
          )}
        </Box>
      )}

      <Dialog open={ratingDialog} onClose={() => setRatingDialog(false)}>
        <DialogTitle>Rate Your Ride</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2, textAlign: 'center' }}>
            <Rating value={rating} onChange={(_, value) => setRating(value || 5)} size="large" />
          </Box>
          <TextField
            fullWidth
            label="Feedback (optional)"
            multiline
            rows={3}
            value={feedback}
            onChange={(e) => setFeedback(e.target.value)}
            margin="normal"
            placeholder="How was your experience?"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRatingDialog(false)}>Cancel</Button>
          <Button onClick={handleRate} variant="contained" disabled={rateRideMutation.isPending}>
            {rateRideMutation.isPending ? <CircularProgress size={20} /> : 'Submit'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
