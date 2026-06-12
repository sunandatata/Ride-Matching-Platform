const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

let availableRides = [];
let currentRides = {};
let completedRides = [];
let driverEarnings = {};

// ========== DISTANCE CALCULATION ==========
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// ========== FARE CALCULATION ==========
function calculateFare(distance) {
  const baseFare = 1.50;
  const perKmRate = 1.25;
  const timeMinutes = distance * 5;
  const perMinRate = 0.35;
  return baseFare + (distance * perKmRate) + (timeMinutes * perMinRate);
}

// ========== AUTH MIDDLEWARE ==========
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

// ========== AUTH ENDPOINTS ==========
app.post('/api/v1/auth/login', (req, res) => {
  res.json({ access_token: 'demo-token', refresh_token: 'demo-refresh' });
});

app.post('/api/v1/auth/refresh', (req, res) => {
  res.json({ access_token: 'demo-token', refresh_token: 'demo-refresh' });
});

app.get('/api/v1/auth/validate', auth, (req, res) => {
  res.json({ valid: true });
});

// ========== RIDE ENDPOINTS ==========

// POST /api/v1/rides - REQUEST RIDE WITH DYNAMIC PRICING
app.post('/api/v1/rides', auth, function(req, res) {
  const pickup = req.body.pickup_location;
  const dropoff = req.body.dropoff_location;

  const distance = calculateDistance(
    pickup.latitude,
    pickup.longitude,
    dropoff.latitude,
    dropoff.longitude
  );

  const estimatedFare = calculateFare(distance);

  const ride = {
    id: 'ride-' + Date.now(),
    rider_id: req.user.id,
    status: 'requested',
    pickup_location: pickup,
    dropoff_location: dropoff,
    distance: Math.round(distance * 100) / 100,
    estimated_fare: Math.round(estimatedFare * 100) / 100,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };

  availableRides.push(ride);
  console.log('Ride created:', ride.id, 'Distance:', ride.distance, 'km', 'Fare: $' + ride.estimated_fare);
  res.json(ride);
});

// GET /api/v1/rides/available - GET AVAILABLE RIDES
app.get('/api/v1/rides/available', auth, (req, res) => {
  res.json(availableRides);
});

// GET /api/v1/rides/current - GET CURRENT RIDE
app.get('/api/v1/rides/current', auth, (req, res) => {
  let rideId = null;

  if (req.user.role === 'rider') {
    for (let id in currentRides) {
      if (currentRides[id].rider_id === req.user.id) {
        rideId = id;
        break;
      }
    }
  } else if (req.user.role === 'driver') {
    for (let id in currentRides) {
      if (currentRides[id].driver_id === req.user.id) {
        rideId = id;
        break;
      }
    }
  }

  res.json(rideId ? currentRides[rideId] : null);
});

// POST /api/v1/rides/:rideId/accept - ACCEPT RIDE
app.post('/api/v1/rides/:rideId/accept', auth, (req, res) => {
  const rideId = req.params.rideId;
  const rideIndex = availableRides.findIndex(r => r.id === rideId);

  if (rideIndex === -1) {
    return res.status(404).json({ error: 'Ride not found' });
  }

  const ride = availableRides[rideIndex];
  availableRides.splice(rideIndex, 1);

  ride.status = 'accepted';
  ride.driver_id = req.user.id;
  ride.driver_name = 'Your Driver';
  ride.driver_rating = 4.8;
  ride.updated_at = new Date().toISOString();

  currentRides[rideId] = ride;

  console.log('Ride accepted:', rideId, 'by driver:', req.user.id);
  res.json(ride);
});

// POST /api/v1/rides/:rideId/start - START RIDE
app.post('/api/v1/rides/:rideId/start', auth, (req, res) => {
  const ride = currentRides[req.params.rideId];
  if (!ride) return res.status(404).json({ error: 'Not found' });

  ride.status = 'in_progress';
  ride.updated_at = new Date().toISOString();
  res.json(ride);
});

// POST /api/v1/rides/:rideId/complete - COMPLETE RIDE
app.post('/api/v1/rides/:rideId/complete', auth, (req, res) => {
  const ride = currentRides[req.params.rideId];
  if (!ride) return res.status(404).json({ error: 'Not found' });

  const actualFare = req.body.fare || ride.estimated_fare;

  ride.status = 'completed';
  ride.actual_fare = actualFare;
  ride.updated_at = new Date().toISOString();

  if (ride.driver_id) {
    if (!driverEarnings[ride.driver_id]) {
      driverEarnings[ride.driver_id] = { total: 0, daily: 0, weekly: 0, rideCount: 0 };
    }
    const driverEarning = actualFare * 0.85;
    driverEarnings[ride.driver_id].total += driverEarning;
    driverEarnings[ride.driver_id].daily += driverEarning;
    driverEarnings[ride.driver_id].weekly += driverEarning;
    driverEarnings[ride.driver_id].rideCount += 1;
  }

  completedRides.push(ride);
  delete currentRides[req.params.rideId];

  res.json(ride);
});

// POST /api/v1/rides/:rideId/cancel - CANCEL RIDE
app.post('/api/v1/rides/:rideId/cancel', auth, (req, res) => {
  const rideId = req.params.rideId;
  const idx = availableRides.findIndex(r => r.id === rideId);

  if (idx !== -1) {
    const ride = availableRides[idx];
    availableRides.splice(idx, 1);
    res.json(ride);
  } else if (currentRides[rideId]) {
    delete currentRides[rideId];
    res.json({ status: 'cancelled' });
  } else {
    res.status(404).json({ error: 'Not found' });
  }
});

// GET /api/v1/rides - GET RIDE HISTORY
app.get('/api/v1/rides', auth, (req, res) => {
  const rides = completedRides.filter(r => r.rider_id === req.user.id);
  res.json({ data: rides, total: rides.length });
});

// GET /api/v1/drivers/rides - GET DRIVER RIDE HISTORY
app.get('/api/v1/drivers/rides', auth, (req, res) => {
  const rides = completedRides.filter(r => r.driver_id === req.user.id);
  res.json({ data: rides, total: rides.length });
});

// POST /api/v1/rides/:rideId/rate - RATE RIDE
app.post('/api/v1/rides/:rideId/rate', auth, (req, res) => {
  const ride = completedRides.find(r => r.id === req.params.rideId);
  if (ride) {
    ride.rating = req.body.rating;
    ride.feedback = req.body.feedback;
  }
  res.json(ride || {});
});

// ========== EARNINGS ENDPOINTS ==========

app.get('/api/v1/drivers/earnings', auth, (req, res) => {
  const earnings = driverEarnings[req.user.id] || { total: 0, daily: 0, weekly: 0, rideCount: 0 };
  res.json(earnings);
});

app.get('/api/v1/drivers/earnings/history', auth, (req, res) => {
  const rides = completedRides.filter(r => r.driver_id === req.user.id);
  const history = rides.map(r => ({
    id: r.id,
    date: r.updated_at,
    distance: r.distance,
    fare: r.actual_fare || r.estimated_fare,
    earning: Math.round((r.actual_fare || r.estimated_fare) * 0.85 * 100) / 100
  }));
  res.json({ data: history, total: history.length, totalEarnings: history.reduce((sum, h) => sum + h.earning, 0) });
});

// ========== HEALTH CHECK ==========
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// ========== 404 ==========
app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// ========== START SERVER ==========
const PORT = 8000;
app.listen(PORT, () => {
  console.log('\n🚀 Backend API v3 running on http://localhost:' + PORT);
  console.log('   ✅ DYNAMIC PRICING ACTIVE');
  console.log('   ✅ Distance calculated from coordinates');
  console.log('   ✅ Earnings tracking enabled\n');
});
