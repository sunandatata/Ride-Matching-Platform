import React, { useState } from 'react'
import {
  Box,
  TextField,
  Button,
  Typography,
  Avatar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
} from '@mui/material'
import { Upload as UploadIcon, Delete as DeleteIcon, Edit as EditIcon, Logout as LogoutIcon } from '@mui/icons-material'
import { useAuth } from '@/hooks/useAuth'
import '../styles/ProfilePage.css'

export const ProfilePage: React.FC = () => {
  const { user, logout } = useAuth()
  const [documentsDialog, setDocumentsDialog] = useState(false)
  const [vehicleDialog, setVehicleDialog] = useState(false)

  const documents = [
    { id: 1, name: 'Driver License', status: 'Verified' },
    { id: 2, name: 'Insurance', status: 'Verified' },
    { id: 3, name: 'Registration', status: 'Verified' },
    { id: 4, name: 'Pollution Certificate', status: 'Verified' },
  ]

  return (
    <Box className="profile-page">
      {/* Header with avatar */}
      <Box className="profile-header">
        <Box className="profile-avatar-container">
          <Avatar
            className="profile-avatar"
            sx={{
              width: 100,
              height: 100,
              background: 'linear-gradient(135deg, var(--color-accent-electric), var(--color-accent-cyan))',
              fontSize: '40px',
              fontWeight: 700,
            }}
          >
            {user?.name.charAt(0).toUpperCase()}
          </Avatar>
        </Box>
        <Box className="profile-info">
          <Typography variant="h3" className="profile-name">
            {user?.name}
          </Typography>
          <Typography className="profile-phone">{user?.phone}</Typography>
          <Box className="profile-badge">
            <span className="badge-dot" />
            <span>Verified Driver</span>
          </Box>
        </Box>
      </Box>

      {/* Account Details Section */}
      <Box className="profile-section">
        <Typography variant="h5" className="section-title">Account Details</Typography>
        <Box className="section-content">
          <Box className="form-group">
            <label>Full Name</label>
            <TextField
              fullWidth
              value={user?.name || ''}
              disabled
              className="profile-input"
            />
          </Box>
          <Box className="form-group">
            <label>Email</label>
            <TextField
              fullWidth
              value={user?.email || ''}
              disabled
              className="profile-input"
            />
          </Box>
          <Box className="form-group">
            <label>Phone</label>
            <TextField
              fullWidth
              value={user?.phone || ''}
              disabled
              className="profile-input"
            />
          </Box>
        </Box>
      </Box>

      {/* Vehicle Information Section */}
      <Box className="profile-section">
        <Box className="section-header">
          <Typography variant="h5" className="section-title">Vehicle Information</Typography>
          <Button
            className="section-edit-btn"
            startIcon={<EditIcon />}
            onClick={() => setVehicleDialog(true)}
          >
            Edit
          </Button>
        </Box>
        <Box className="section-content">
          <Box className="vehicle-grid">
            <Box className="info-item">
              <Typography className="info-label">Vehicle Type</Typography>
              <Typography className="info-value">SUV - Honda CR-V</Typography>
            </Box>
            <Box className="info-item">
              <Typography className="info-label">License Plate</Typography>
              <Typography className="info-value">ABC-1234</Typography>
            </Box>
            <Box className="info-item">
              <Typography className="info-label">Insurance Expiry</Typography>
              <Typography className="info-value">Dec 31, 2025</Typography>
            </Box>
            <Box className="info-item">
              <Typography className="info-label">Registration</Typography>
              <Typography className="info-value">Active</Typography>
            </Box>
          </Box>
        </Box>
      </Box>

      {/* Documents Section */}
      <Box className="profile-section">
        <Box className="section-header">
          <Typography variant="h5" className="section-title">Documents</Typography>
          <Button
            className="section-edit-btn"
            startIcon={<UploadIcon />}
            onClick={() => setDocumentsDialog(true)}
          >
            Upload
          </Button>
        </Box>
        <Box className="section-content">
          <Box className="documents-list">
            {documents.map((doc) => (
              <Box key={doc.id} className="document-item">
                <Box className="document-info">
                  <Typography className="document-name">{doc.name}</Typography>
                  <Box className="document-status verified">
                    <span className="status-dot" />
                    {doc.status}
                  </Box>
                </Box>
                <IconButton size="small" className="document-delete">
                  <DeleteIcon />
                </IconButton>
              </Box>
            ))}
          </Box>
        </Box>
      </Box>

      {/* Logout Button */}
      <Box className="profile-actions">
        <Button
          fullWidth
          className="logout-btn"
          startIcon={<LogoutIcon />}
          onClick={() => logout()}
        >
          Logout
        </Button>
      </Box>

      {/* Vehicle Edit Dialog */}
      <Dialog
        open={vehicleDialog}
        onClose={() => setVehicleDialog(false)}
        PaperProps={{
          sx: {
            backgroundColor: 'var(--color-primary-main)',
            border: '1px solid rgba(0, 255, 136, 0.2)',
          }
        }}
      >
        <DialogTitle sx={{ fontFamily: 'var(--font-display)', color: 'var(--color-white)' }}>
          Edit Vehicle Information
        </DialogTitle>
        <DialogContent>
          <Box className="dialog-form">
            <TextField fullWidth label="Vehicle Type" margin="normal" />
            <TextField fullWidth label="License Plate" margin="normal" />
            <TextField fullWidth label="Insurance Expiry" margin="normal" type="date" />
          </Box>
        </DialogContent>
        <DialogActions sx={{ padding: '16px' }}>
          <Button onClick={() => setVehicleDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => setVehicleDialog(false)}
            className="dialog-save-btn"
          >
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* Document Upload Dialog */}
      <Dialog
        open={documentsDialog}
        onClose={() => setDocumentsDialog(false)}
        PaperProps={{
          sx: {
            backgroundColor: 'var(--color-primary-main)',
            border: '1px solid rgba(0, 255, 136, 0.2)',
          }
        }}
      >
        <DialogTitle sx={{ fontFamily: 'var(--font-display)', color: 'var(--color-white)' }}>
          Upload Document
        </DialogTitle>
        <DialogContent>
          <Box className="dialog-form">
            <TextField
              select
              fullWidth
              label="Document Type"
              margin="normal"
              defaultValue="driver-license"
            >
              <option value="driver-license">Driver License</option>
              <option value="insurance">Insurance</option>
              <option value="registration">Registration</option>
              <option value="pollution">Pollution Certificate</option>
            </TextField>
            <Button
              variant="outlined"
              fullWidth
              sx={{ mt: 2 }}
              startIcon={<UploadIcon />}
              className="upload-file-btn"
            >
              Choose File
            </Button>
          </Box>
        </DialogContent>
        <DialogActions sx={{ padding: '16px' }}>
          <Button onClick={() => setDocumentsDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => setDocumentsDialog(false)}
            className="dialog-save-btn"
          >
            Upload
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
