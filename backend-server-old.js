// Ride Sharing Platform Backend API - Complete Implementation
const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

// ============ IN-MEMORY STORAGE ============
let availableRides = [];
let currentRides = {}; // rideId -> ride mapping
let driverCurrentRides = {}; // driver_id -> rideId mapping
let completedRides = [];
let driverEarnings = {}; // driver_id -> earnings object

// ============ UTILITY FUNCTIONS ============

// Haversine distance calculation (in km)
const calculateDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371; // Earth's radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

// Realistic fare calculation: $1.50 base + $1.25/km + time-based ($0.35/min based on 5min/km)
const calculateFare = (distance) => {
  const baseFare = 1.50;
  const perKmRate = 1.25;
  const timeMinutes = distance * 5; // Assume average 5 minutes per km
  const perMinRate = 0.35;
  return baseFare + (distance * perKmRate) + (timeMinutes * perMinRate);
};

// ============ MIDDLEWARE ============

// Authentication middleware
const auth = (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];

  if (token && token.startsWith('demo')) {
    let userId = 'demo-user-' + Date.now();
    let role = 'rider';

    if (token.includes('rider')) {
      userId = 'demo-rider-001';
      role = 'rider';
    } else if (token.includes('driver')) {
      userId = 'demo-driver-001';
      role = 'driver';
    }

    req.user = { id: userId, role: role };
    next();
  } else {
    res.status(401).json({ error: 'Unauthorized' });
  }
};

// ============ AUTH ENDPOINTS ============

app.post('/api/v1/auth/login', (req, res) => {
  const { phone, password } = req.body;
  if (phone && password) {
    res.json({
      access_token: 'demo-token',
      refresh_token: 'demo-refresh',
      user: { id: 'user-' + Date.now(), phone, name: 'Demo User', role: 'rider', verified: true }
    });
  } else {
    res.status(400).json({ error: 'Invalid credentials' });
  }
});

app.post('/api/v1/auth/refresh', (req, res) => {
  res.json({ access_token: 'demo-token', refresh_token: 'demo-refresh' });
});

app.get('/api/v1/auth/validate', auth, (req, res) => {
  res.json({ valid: true });
});

// ============ RIDE ENDPOINTS ============

// POST /api/v1/rides - Request a ride with dynamic pricing
app.post('/api/v1/rides', auth, (req, res) => {
  const { pickup_location, dropoff_location } = req.body;

  // CALCULATE DISTANCE AND FARE DYNAMICALLY
  const distance = calculateDistance(
    pickup_location.latitude,
    pickup_location.longitude,
    dropoff_location.latitude,
    dropoff_location.longitude
  );
  // Write to file for debugging
  const fs = require('fs');
  fs.appendFileSync('/tmp/backend-debug.log', `CALC: ${distance.toFixed(2)}km\n`);

  const estimated_fare = calculateFare(distance);

  const ride = {
    id: `ride-${Date.now()}`,
    rider_id: req.user.id,
    status: 'requested',
    pickup_location,
    dropoff_location,
    distance: Math.round(distance * 100) / 100,
    estimated_fare: Math.round(estimated_fare * 100) / 100,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };

  availableRides.push(ride);
  console.log(`✓ Ride created: ${ride.id} (${ride.distance}km, $${ride.estimated_fare})`);
  res.json(ride);
});

// GET /api/v1/rides/available - Get rides available for driver
app.get('/api/v1/rides/available', auth, (req, res) => {
  res.json(availableRides);
});

// GET /api/v1/rides/current - Get current ride (works for both rider and driver)
app.get('/api/v1/rides/current', auth, (req, res) => {
  let rideId;

  if (req.user.role === 'rider') {
    // Rider: find by rider_id
    rideId = Object.keys(currentRides).find(id => {
      const ride = currentRides[id];
      return ride && ride.rider_id === req.user.id;
    });
  } else if (req.user.role === 'driver') {
    // Driver: find by driver_id
    rideId = driverCurrentRides[req.user.id];
  }

  if (rideId && currentRides[rideId]) {
    res.json(currentRides[rideId]);
  } else {
    res.json(null);
  }
});

// POST /api/v1/rides/:rideId/accept - Driver accepts ride
app.post('/api/v1/rides/:rideId/accept', auth, (req, res) => {
  const { rideId } = req.params;
  const driverId = req.user.id; // USE AUTHENTICATED DRIVER ID

  const rideIndex = availableRides.findIndex(r => r.id === rideId);
  if (rideIndex === -1) {
    return res.status(404).json({ error: 'Ride not found' });
  }

  const ride = availableRides[rideIndex];
  availableRides.splice(rideIndex, 1);

  // Update ride with driver info
  ride.status = 'accepted';
  ride.driver_id = driverId; // ASSIGN ACTUAL DRIVER
  ride.driver_name = 'Your Driver';
  ride.driver_rating = 4.8;
  ride.updated_at = new Date().toISOString();

  // Track for driver and current rides
  currentRides[rideId] = ride;
  driverCurrentRides[driverId] = rideId;

  console.log(`✓ Ride accepted: ${rideId} by driver ${driverId}`);
  res.json(ride);
});

// POST /api/v1/rides/:rideId/start - Start the ride
app.post('/api/v1/rides/:rideId/start', auth, (req, res) => {
  const rideId = req.params.rideId;
  const ride = currentRides[rideId];
  if (!ride) {
    return res.status(404).json({ error: 'Ride not found' });
  }

  ride.status = 'in_progress';
  ride.started_at = new Date().toISOString();
  ride.updated_at = ride.started_at;
  console.log(`✓ Ride started: ${rideId}`);
  res.json(ride);
});

// POST /api/v1/rides/:rideId/complete - Complete the ride
app.post('/api/v1/rides/:rideId/complete', auth, (req, res) => {
  const { fare } = req.body;
  const rideId = req.params.rideId;
  const ride = currentRides[rideId];

  if (!ride) {
    return res.status(404).json({ error: 'Ride not found' });
  }

  const actualFare = fare || ride.estimated_fare;

  // Update ride
  ride.status = 'completed';
  ride.actual_fare = actualFare;
  ride.completed_at = new Date().toISOString();
  ride.updated_at = ride.completed_at;

  // UPDATE DRIVER EARNINGS
  const driverId = ride.driver_id;
  if (driverId) {
    if (!driverEarnings[driverId]) {
      driverEarnings[driverId] = { total: 0, daily: 0, weekly: 0, rideCount: 0, lastUpdated: new Date() };
    }

    const earnings = driverEarnings[driverId];
    const completionDate = new Date();
    const driverEarning = actualFare * 0.85; // 85% to driver

    earnings.total += driverEarning;
    earnings.rideCount += 1;

    // Reset daily/weekly if new period
    const lastUpdate = new Date(earnings.lastUpdated);
    if (completionDate.toDateString() !== lastUpdate.toDateString()) {
      earnings.daily = 0;
    }
    if (Math.floor(completionDate.getTime() / (7 * 24 * 60 * 60 * 1000)) !==
        Math.floor(lastUpdate.getTime() / (7 * 24 * 60 * 60 * 1000))) {
      earnings.weekly = 0;
    }

    earnings.daily += driverEarning;
    earnings.weekly += driverEarning;
    earnings.lastUpdated = completionDate.toISOString();

    console.log(`✓ Earnings updated for ${driverId}: +$${driverEarning.toFixed(2)}`);
  }

  completedRides.push(ride);
  delete currentRides[rideId];
  delete driverCurrentRides[driverId];

  console.log(`✓ Ride completed: ${rideId}, Fare: $${actualFare}`);
  res.json(ride);
});

// POST /api/v1/rides/:rideId/cancel - Cancel a ride
app.post('/api/v1/rides/:rideId/cancel', auth, (req, res) => {
  const { reason } = req.body;
  const rideId = req.params.rideId;

  let rideIndex = availableRides.findIndex(r => r.id === rideId);
  if (rideIndex !== -1) {
    const ride = availableRides[rideIndex];
    ride.status = 'cancelled';
    availableRides.splice(rideIndex, 1);
    completedRides.push(ride);
    console.log(`✓ Ride cancelled: ${rideId}`);
    return res.json(ride);
  }

  const ride = currentRides[rideId];
  if (ride) {
    ride.status = 'cancelled';
    delete currentRides[rideId];
    completedRides.push(ride);
    console.log(`✓ Ride cancelled: ${rideId}`);
    return res.json(ride);
  }

  res.status(404).json({ error: 'Ride not found' });
});

// GET /api/v1/rides - Get rider's ride history
app.get('/api/v1/rides', auth, (req, res) => {
  const rides = completedRides.filter(r => r.rider_id === req.user.id);
  res.json({ data: rides, total: rides.length });
});

// GET /api/v1/drivers/rides - Get driver's ride history
app.get('/api/v1/drivers/rides', auth, (req, res) => {
  const rides = completedRides.filter(r => r.driver_id === req.user.id);
  res.json({ data: rides, total: rides.length });
});

// POST /api/v1/rides/:rideId/rate - Rate a ride
app.post('/api/v1/rides/:rideId/rate', auth, (req, res) => {
  const { rating, feedback } = req.body;
  const ride = completedRides.find(r => r.id === req.params.rideId);

  if (!ride) {
    return res.status(404).json({ error: 'Ride not found' });
  }

  ride.rating = rating;
  ride.feedback = feedback;
  console.log(`✓ Ride rated: ${req.params.rideId}`);
  res.json(ride);
});

// ============ DRIVER EARNINGS ENDPOINTS ============

// GET /api/v1/drivers/earnings - Get earnings summary
app.get('/api/v1/drivers/earnings', auth, (req, res) => {
  const driverId = req.user.id;
  const earnings = driverEarnings[driverId] || {
    total: 0,
    daily: 0,
    weekly: 0,
    rideCount: 0,
    lastUpdated: new Date().toISOString()
  };

  res.json(earnings);
});

// GET /api/v1/drivers/earnings/history - Get earnings history
app.get('/api/v1/drivers/earnings/history', auth, (req, res) => {
  const driverId = req.user.id;
  const rides = completedRides.filter(r => r.driver_id === driverId);

  const history = rides.map(ride => ({
    id: ride.id,
    date: ride.completed_at,
    passenger: ride.rider_id,
    distance: ride.distance,
    fare: ride.actual_fare,
    driverEarning: Math.round(ride.actual_fare * 0.85 * 100) / 100,
    pickup: ride.pickup_location.address,
    dropoff: ride.dropoff_location.address
  }));

  res.json({
    data: history,
    total: history.length,
    totalEarnings: history.reduce((sum, h) => sum + h.driverEarning, 0)
  });
});

// ============ HEALTH CHECK ============

app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// ============ 404 HANDLER ============

app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// ============ START SERVER ============

const PORT = 8000;
app.listen(PORT, () => {
  console.log(`🚀 Backend API running on http://localhost:${PORT}`);
  console.log('   ✓ Dynamic pricing enabled');
  console.log('   ✓ Driver earnings tracking enabled');
  console.log('   ✓ Real-time ride matching ready');
});
