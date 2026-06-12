import React from 'react'
import {
  Box,
  Typography,
  Card,
  CardContent,
  Grid,
  TextField,
  Button,
  Switch,
  FormControlLabel,
} from '@mui/material'
import { useAuth } from '@/hooks/useAuth'

export const SettingsPage: React.FC = () => {
  const { user } = useAuth()

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" sx={{ fontWeight: 'bold', mb: 3 }}>
        Settings
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                Account Information
              </Typography>
              <TextField
                fullWidth
                label="Name"
                value={user?.name || ''}
                disabled
                margin="normal"
              />
              <TextField
                fullWidth
                label="Email"
                value={user?.email || ''}
                disabled
                margin="normal"
              />
              <TextField
                fullWidth
                label="Phone"
                value={user?.phone || ''}
                disabled
                margin="normal"
              />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                Notifications
              </Typography>
              <FormControlLabel
                control={<Switch defaultChecked />}
                label="Email Notifications"
              />
              <FormControlLabel
                control={<Switch defaultChecked />}
                label="SMS Alerts"
              />
              <FormControlLabel
                control={<Switch />}
                label="Marketing Communications"
              />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                Security
              </Typography>
              <Button variant="contained" fullWidth sx={{ mb: 1 }}>
                Change Password
              </Button>
              <Button variant="outlined" fullWidth>
                Two-Factor Authentication
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                API Keys
              </Typography>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                Manage your API keys for integrations
              </Typography>
              <Button variant="outlined" fullWidth>
                Generate New Key
              </Button>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
