import React, { useState } from 'react'
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Avatar,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Switch,
  FormControlLabel,
} from '@mui/material'
import { Delete as DeleteIcon, Add as AddIcon, Logout as LogoutIcon } from '@mui/icons-material'
import { useAuth } from '@/hooks/useAuth'
import { usePaymentMethods, useAddPaymentMethod, useDeletePaymentMethod } from '@/hooks/usePayment'

export const ProfilePage: React.FC = () => {
  const { user, logout } = useAuth()
  const { data: paymentMethods } = usePaymentMethods()
  const addPaymentMutation = useAddPaymentMethod()
  const deletePaymentMutation = useDeletePaymentMethod()
  const [paymentDialog, setPaymentDialog] = useState(false)
  const [cardNumber, setCardNumber] = useState('')
  const [expiryDate, setExpiryDate] = useState('')
  const [cvv, setCvv] = useState('')

  const handleAddPayment = () => {
    if (cardNumber && expiryDate && cvv) {
      addPaymentMutation.mutate(
        { type: 'credit', last4: cardNumber.slice(-4), card_number: cardNumber, expiry_date: expiryDate, cvv },
        { onSuccess: () => { setPaymentDialog(false); setCardNumber(''); setExpiryDate(''); setCvv('') } }
      )
    }
  }

  return (
    <Box sx={{ p: 2, pb: 10 }}>
      <Typography variant="h5" sx={{ fontWeight: 'bold', mb: 3 }}>Profile</Typography>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <Avatar sx={{ width: 80, height: 80, mr: 2, backgroundColor: '#1976d2' }}>
              {user?.name.charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6">{user?.name}</Typography>
              <Typography variant="body2" color="textSecondary">{user?.phone}</Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>Account Details</Typography>
          <TextField fullWidth label="Name" value={user?.name || ''} disabled margin="normal" />
          <TextField fullWidth label="Email" value={user?.email || ''} disabled margin="normal" />
          <TextField fullWidth label="Phone" value={user?.phone || ''} disabled margin="normal" />
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Payment Methods</Typography>
            <IconButton onClick={() => setPaymentDialog(true)} color="primary" size="small">
              <AddIcon />
            </IconButton>
          </Box>
          {paymentMethods && paymentMethods.length > 0 ? (
            <List>
              {paymentMethods.map((method) => (
                <ListItem key={method.id} secondaryAction={
                  <IconButton edge="end" onClick={() => deletePaymentMutation.mutate(method.id)}>
                    <DeleteIcon />
                  </IconButton>
                }>
                  <ListItemText
                    primary={`${method.type.toUpperCase()} ****${method.last4}`}
                    secondary={method.default ? 'Default' : ''}
                  />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography variant="body2" color="textSecondary">No payment methods</Typography>
          )}
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>Preferences</Typography>
          <FormControlLabel control={<Switch defaultChecked />} label="SMS Notifications" />
          <FormControlLabel control={<Switch defaultChecked />} label="Email Notifications" />
        </CardContent>
      </Card>

      <Button fullWidth variant="contained" color="error" startIcon={<LogoutIcon />} onClick={() => logout()}>
        Logout
      </Button>

      <Dialog open={paymentDialog} onClose={() => setPaymentDialog(false)}>
        <DialogTitle>Add Payment Method</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Card Number"
            value={cardNumber}
            onChange={(e) => setCardNumber(e.target.value)}
            margin="normal"
            placeholder="1234 5678 9012 3456"
          />
          <TextField
            fullWidth
            label="Expiry Date"
            value={expiryDate}
            onChange={(e) => setExpiryDate(e.target.value)}
            margin="normal"
            placeholder="MM/YY"
          />
          <TextField
            fullWidth
            label="CVV"
            type="password"
            value={cvv}
            onChange={(e) => setCvv(e.target.value)}
            margin="normal"
            placeholder="123"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaymentDialog(false)}>Cancel</Button>
          <Button onClick={handleAddPayment} variant="contained">Add Card</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
