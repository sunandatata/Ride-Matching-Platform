const express = require('express');
const cors = require('cors');
const app = express();
app.use(cors());
app.use(express.json());

let availableRides = [];

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

function calculateFare(distance) {
  const baseFare = 1.50;
  const perKmRate = 1.25;
  const perMinRate = 0.35;
  const timeMinutes = distance * 5;
  return baseFare + (distance * perKmRate) + (timeMinutes * perMinRate);
}

function auth(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];
  if (token && token.startsWith('demo')) {
    req.user = { id: 'demo-rider-001', role: 'rider' };
    next();
  } else {
    res.status(401).json({ error: 'Unauthorized' });
  }
}

app.post('/api/v1/rides', auth, (req, res) => {
  const p = req.body.pickup_location;
  const d = req.body.dropoff_location;
  const distance = haversine(p.latitude, p.longitude, d.latitude, d.longitude);
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
  };

  availableRides.push(ride);
  console.log(`✓ HAVERSINE WORKING: ${ride.distance}km | $${ride.estimated_fare}`);
  res.json(ride);
});

app.get('/health', (req, res) => res.json({ status: 'ok' }));

app.listen(7000, () => console.log('✓ Backend TEST on port 7000 with HAVERSINE'));
