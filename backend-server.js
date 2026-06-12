const express = require('express');
const cors = require('cors');
const app = express();
app.use(cors());
app.use(express.json());

let availableRides = [];
let currentRides = {};
let driverEarnings = {};

// ========== DYNAMIC PRICING - WORKING VERSION ==========
// Haversine formula for accurate distance calculation
function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1*Math.PI/180) * Math.cos(lat2*Math.PI/180) *
            Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

// Realistic 3-component fare formula
function calculateFare(distance) {
  const baseFare = 1.50;        // Base fare
  const perKmRate = 1.25;       // Per km rate
  const perMinRate = 0.35;      // Per minute rate (5 min/km assumption)
  const timeMinutes = distance * 5;
  return baseFare + (distance * perKmRate) + (timeMinutes * perMinRate);
}

// ========== AUTHENTICATION ==========
function auth(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];
  if (token && token.startsWith('demo')) {
    req.user = {
      id: token.includes('rider') ? 'demo-rider-001' : 'demo-driver-001',
      role: token.includes('rider') ? 'rider' : 'driver'
    };
    next();
  } else {
    res.status(401).json({ error: 'Unauthorized' });
  }
}

// ========== API ENDPOINTS ==========

// AUTH ENDPOINTS
app.post('/api/v1/auth/login', (req, res) => {
  res.json({ access_token: 'demo-token', refresh_token: 'demo-refresh', user: { id: 'user-' + Date.now() } });
});

app.post('/api/v1/auth/refresh', (req, res) => {
  res.json({ access_token: 'demo-token', refresh_token: 'demo-refresh' });
});

app.get('/api/v1/auth/validate', auth, (req, res) => {
  res.json({ valid: true });
});

// RIDE CREATION - WITH DYNAMIC PRICING
app.post('/api/v1/rides', auth, (req, res) => {
  const p = req.body.pickup_location;
  const d = req.body.dropoff_location;

  // CALCULATE DISTANCE FROM COORDINATES
  const distance = haversine(p.latitude, p.longitude, d.latitude, d.longitude);
  // CALCULATE FARE FROM DISTANCE
  const fare = calculateFare(distance);

  const ride = {
    id: 'ride-' + Date.now(),
    rider_id: req.user.id,
    status: 'requested',
    pickup_location: p,
    dropoff_location: d,
    distance: Math.round(distance * 100) / 100,
    estimated_fare: Math.round(fare * 100) / 100,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };

  availableRides.push(ride);
  console.log(`✓ Ride created: ${ride.distance}km | $${ride.estimated_fare}`);
  res.json(ride);
});

// GET AVAILABLE RIDES
app.get('/api/v1/rides/available', auth, (req, res) => {
  res.json(availableRides);
});

// GET CURRENT RIDE (for both rider and driver)
app.get('/api/v1/rides/current', auth, (req, res) => {
  let rideId = null;
  for (let id in currentRides) {
    const r = currentRides[id];
    if (req.user.role === 'rider' && r.rider_id === req.user.id) {
      rideId = id;
      break;
    }
    if (req.user.role === 'driver' && r.driver_id === req.user.id) {
      rideId = id;
      break;
    }
  }
  res.json(rideId ? currentRides[rideId] : null);
});

// ACCEPT RIDE - with authenticated driver
app.post('/api/v1/rides/:rideId/accept', auth, (req, res) => {
  const idx = availableRides.findIndex(r => r.id === req.params.rideId);
  if (idx === -1) return res.status(404).json({ error: 'Not found' });

  const ride = availableRides[idx];
  availableRides.splice(idx, 1);

  ride.status = 'accepted';
  ride.driver_id = req.user.id;  // Use authenticated driver ID
  ride.driver_name = 'Your Driver';
  ride.driver_rating = 4.8;

  currentRides[req.params.rideId] = ride;
  console.log(`✓ Ride accepted: ${req.params.rideId}`);
  res.json(ride);
});

// START RIDE
app.post('/api/v1/rides/:rideId/start', auth, (req, res) => {
  const ride = currentRides[req.params.rideId];
  if (!ride) return res.status(404).json({ error: 'Not found' });
  ride.status = 'in_progress';
  res.json(ride);
});

// COMPLETE RIDE - with earnings calculation
app.post('/api/v1/rides/:rideId/complete', auth, (req, res) => {
  const ride = currentRides[req.params.rideId];
  if (!ride) return res.status(404).json({ error: 'Not found' });

  const fare = req.body.fare || ride.estimated_fare;
  ride.status = 'completed';
  ride.actual_fare = fare;

  // UPDATE DRIVER EARNINGS (85% commission)
  if (ride.driver_id) {
    if (!driverEarnings[ride.driver_id]) {
      driverEarnings[ride.driver_id] = { total: 0, daily: 0, weekly: 0, rideCount: 0 };
    }
    const earning = fare * 0.85;
    driverEarnings[ride.driver_id].total += earning;
    driverEarnings[ride.driver_id].daily += earning;
    driverEarnings[ride.driver_id].weekly += earning;
    driverEarnings[ride.driver_id].rideCount += 1;
    console.log(`✓ Earnings updated: +$${earning.toFixed(2)}`);
  }

  delete currentRides[req.params.rideId];
  res.json(ride);
});

// CANCEL RIDE
app.post('/api/v1/rides/:rideId/cancel', auth, (req, res) => {
  const idx = availableRides.findIndex(r => r.id === req.params.rideId);
  if (idx !== -1) {
    availableRides.splice(idx, 1);
    return res.json({});
  }
  if (currentRides[req.params.rideId]) {
    delete currentRides[req.params.rideId];
    return res.json({});
  }
  res.status(404).json({ error: 'Not found' });
});

// RIDE HISTORY
app.get('/api/v1/rides', auth, (req, res) => res.json({ data: [], total: 0 }));
app.get('/api/v1/drivers/rides', auth, (req, res) => res.json({ data: [], total: 0 }));

// RATE RIDE
app.post('/api/v1/rides/:rideId/rate', auth, (req, res) => res.json({}));

// DRIVER EARNINGS - ENDPOINTS
app.get('/api/v1/drivers/earnings', auth, (req, res) => {
  const e = driverEarnings[req.user.id] || { total: 0, daily: 0, weekly: 0, rideCount: 0 };
  res.json(e);
});

app.get('/api/v1/drivers/earnings/history', auth, (req, res) => {
  res.json({ data: [], total: 0, totalEarnings: 0 });
});

// HEALTH CHECK
app.get('/health', (req, res) => res.json({ status: 'ok' }));

// START SERVER
app.listen(8000, () => {
  console.log('\n🚀 Backend API running on http://localhost:8000');
  console.log('   ✅ DYNAMIC PRICING ENABLED (Haversine distance)');
  console.log('   ✅ Realistic 3-component fare calculation');
  console.log('   ✅ Authenticated driver ID assignment');
  console.log('   ✅ Driver earnings tracking with 85% commission\n');
});
