console.log('STARTING BACKEND WITH LOGGING...');

const express = require('express');
const cors = require('cors');
const app = express();
app.use(cors());
app.use(express.json());

let availableRides = [];
let currentRides = {};

console.log('Defining calculateDistance...');
const calculateDistance = (lat1, lon1, lat2, lon2) => {
  console.log(`DISTANCE_CALC: lat1=${lat1}, lon1=${lon1}, lat2=${lat2}, lon2=${lon2}`);
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1*Math.PI/180) * Math.cos(lat2*Math.PI/180) * Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  const result = R * c;
  console.log(`DISTANCE_RESULT: ${result.toFixed(2)}km`);
  return result;
};

console.log('Defining calculateFare...');
const calculateFare = (distance) => {
  const fare = 1.50 + (distance * 1.25) + ((distance * 5) * 0.35);
  console.log(`FARE_RESULT: $${fare.toFixed(2)}`);
  return fare;
};

const auth = (req, res, next) => {
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
};

console.log('Setting up POST /api/v1/rides endpoint...');
app.post('/api/v1/rides', auth, (req, res) => {
  console.log('>>> POST /api/v1/rides called');
  const { pickup_location, dropoff_location } = req.body;
  console.log(`>>> Input: pickup=${JSON.stringify(pickup_location)}, dropoff=${JSON.stringify(dropoff_location)}`);

  const distance = calculateDistance(
    pickup_location.latitude,
    pickup_location.longitude,
    dropoff_location.latitude,
    dropoff_location.longitude
  );
  const estimated_fare = calculateFare(distance);

  console.log(`>>> Creating ride object with distance=${distance.toFixed(2)}, fare=${estimated_fare.toFixed(2)}`);

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

  console.log(`>>> Returning ride: distance=${ride.distance}, fare=${ride.estimated_fare}`);
  availableRides.push(ride);
  res.json(ride);
});

app.get('/api/v1/rides/available', auth, (req, res) => {
  res.json(availableRides);
});

app.get('/api/v1/rides/current', auth, (req, res) => {
  res.json(null);
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

app.listen(8000, () => {
  console.log('✅ TEST BACKEND LISTENING ON PORT 8000');
  console.log('✅ LOGGING ENABLED - All calculations will be logged');
});
