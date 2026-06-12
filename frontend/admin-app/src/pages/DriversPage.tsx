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
  CheckCircle as ApprovedIcon,
  PendingActions as PendingIcon,
  Block as SuspendedIcon,
  Star as RatingIcon,
} from '@mui/icons-material'
import { useDrivers, useApproveDriver, useRejectDriver, useSuspendDriver } from '@/hooks/useDrivers'
import { DataTable } from '@/components/DataTable'
import { MetricCard } from '@/components/MetricCard'
import { Driver } from '@/types'

export const DriversPage: React.FC = () => {
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [selectedDriver, setSelectedDriver] = useState<Driver | null>(null)
  const [actionDialog, setActionDialog] = useState<{
    open: boolean
    action: 'approve' | 'reject' | 'suspend' | null
  }>({ open: false, action: null })
  const [reason, setReason] = useState('')

  const { data, isLoading } = useDrivers({
    page: page + 1,
    per_page: rowsPerPage,
  })
  const approveMutation = useApproveDriver()
  const rejectMutation = useRejectDriver()
  const suspendMutation = useSuspendDriver()
  const drivers = data?.data || []
  const approvedCount = drivers.filter((driver) => driver.approval_status === 'approved').length
  const pendingCount = drivers.filter((driver) => driver.approval_status === 'pending').length
  const suspendedCount = drivers.filter((driver) => driver.status === 'suspended').length
  const averageRating = drivers.length
    ? drivers.reduce((sum, driver) => sum + driver.rating, 0) / drivers.length
    : 0

  const handleApprove = async () => {
    if (selectedDriver) {
      approveMutation.mutate(selectedDriver.id)
      setActionDialog({ open: false, action: null })
      setSelectedDriver(null)
    }
  }

  const handleReject = async () => {
    if (selectedDriver) {
      rejectMutation.mutate({ driverId: selectedDriver.id, reason })
      setActionDialog({ open: false, action: null })
      setSelectedDriver(null)
      setReason('')
    }
  }

  const handleSuspend = async () => {
    if (selectedDriver) {
      suspendMutation.mutate({ driverId: selectedDriver.id, reason })
      setActionDialog({ open: false, action: null })
      setSelectedDriver(null)
      setReason('')
    }
  }

  const columns = [
    {
      id: 'name',
      label: 'Name',
      minWidth: 150,
      searchAccessor: (row: Driver) => `${row.name} ${row.email || ''} ${row.phone}`,
    },
    {
      id: 'phone',
      label: 'Phone',
      minWidth: 130,
      searchAccessor: (row: Driver) => row.phone,
    },
    {
      id: 'approval_status',
      label: 'Approval Status',
      minWidth: 120,
      sortAccessor: (row: Driver) => row.approval_status,
      render: (row: Driver) => (
        <Chip
          label={row.approval_status.replace('_', ' ')}
          color={
            row.approval_status === 'approved'
              ? 'success'
              : row.approval_status === 'pending'
                ? 'warning'
                : 'error'
          }
          size="small"
        />
      ),
    },
    {
      id: 'status',
      label: 'Status',
      minWidth: 100,
      sortAccessor: (row: Driver) => row.status,
      render: (row: Driver) => (
        <Chip
          label={row.status}
          color={row.status === 'active' ? 'success' : 'default'}
          size="small"
        />
      ),
    },
    {
      id: 'rating',
      label: 'Rating',
      minWidth: 80,
      sortAccessor: (row: Driver) => row.rating,
      render: (row: Driver) => `${row.rating.toFixed(1)}/5`,
    },
    {
      id: 'total_rides',
      label: 'Rides',
      minWidth: 80,
      sortAccessor: (row: Driver) => row.total_rides,
    },
    {
      id: 'actions',
      label: 'Actions',
      minWidth: 200,
      sortable: false,
      render: (row: Driver) => (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {row.approval_status === 'pending' && (
            <>
              <Button
                size="small"
                variant="contained"
                color="success"
                onClick={() => {
                  setSelectedDriver(row)
                  setActionDialog({ open: true, action: 'approve' })
                }}
              >
                Approve
              </Button>
              <Button
                size="small"
                variant="contained"
                color="error"
                onClick={() => {
                  setSelectedDriver(row)
                  setActionDialog({ open: true, action: 'reject' })
                }}
              >
                Reject
              </Button>
            </>
          )}
          {row.status === 'active' && row.approval_status === 'approved' && (
            <Button
              size="small"
              variant="outlined"
              color="error"
              onClick={() => {
                setSelectedDriver(row)
                setActionDialog({ open: true, action: 'suspend' })
              }}
            >
              Suspend
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
          Driver Management
        </Typography>
        <Typography sx={{ color: '#64748B', mt: 0.5 }}>
          Review applications, monitor availability, and manage account status.
        </Typography>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Approved" value={approvedCount} icon={<ApprovedIcon />} tone="green" isLoading={isLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Pending" value={pendingCount} icon={<PendingIcon />} tone="amber" isLoading={isLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Suspended" value={suspendedCount} icon={<SuspendedIcon />} tone="slate" isLoading={isLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard title="Avg Rating" value={averageRating.toFixed(2)} icon={<RatingIcon />} tone="violet" isLoading={isLoading} />
        </Grid>
      </Grid>

      <Card sx={{ borderRadius: 2, boxShadow: '0 12px 30px rgba(15, 23, 42, 0.08)', border: '1px solid #E2E8F0' }}>
        <CardContent sx={{ p: 0 }}>
          <DataTable
            columns={columns}
            rows={drivers}
            isLoading={isLoading}
            page={page}
            rowsPerPage={rowsPerPage}
            total={data?.total || 0}
            onPageChange={setPage}
            onRowsPerPageChange={setRowsPerPage}
            searchPlaceholder="Search drivers by name, phone, or email"
            filters={[
              {
                id: 'approval_status',
                label: 'Approval',
                options: [
                  { value: 'approved', label: 'Approved' },
                  { value: 'pending', label: 'Pending' },
                  { value: 'rejected', label: 'Rejected' },
                ],
                getValue: (row: Driver) => row.approval_status,
              },
              {
                id: 'status',
                label: 'Status',
                options: [
                  { value: 'active', label: 'Active' },
                  { value: 'inactive', label: 'Inactive' },
                  { value: 'suspended', label: 'Suspended' },
                ],
                getValue: (row: Driver) => row.status,
              },
            ]}
          />
        </CardContent>
      </Card>

      <Dialog open={actionDialog.open} onClose={() => setActionDialog({ open: false, action: null })}>
        <DialogTitle>
          {actionDialog.action === 'approve' && 'Approve Driver'}
          {actionDialog.action === 'reject' && 'Reject Driver'}
          {actionDialog.action === 'suspend' && 'Suspend Driver'}
        </DialogTitle>
        <DialogContent>
          {(actionDialog.action === 'reject' || actionDialog.action === 'suspend') && (
            <TextField
              fullWidth
              label="Reason"
              multiline
              rows={4}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              margin="normal"
              placeholder="Enter reason for this action"
            />
          )}
          {actionDialog.action === 'approve' && (
            <Typography>
              Are you sure you want to approve driver {selectedDriver?.name}?
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialog({ open: false, action: null })}>
            Cancel
          </Button>
          <Button
            onClick={() => {
              if (actionDialog.action === 'approve') handleApprove()
              else if (actionDialog.action === 'reject') handleReject()
              else if (actionDialog.action === 'suspend') handleSuspend()
            }}
            variant="contained"
            color={actionDialog.action === 'approve' ? 'success' : 'error'}
          >
            {actionDialog.action === 'approve' ? 'Approve' : actionDialog.action === 'reject' ? 'Reject' : 'Suspend'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
