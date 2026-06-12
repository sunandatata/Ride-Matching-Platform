const express = require('express');
const app = express();
app.use(express.json());

console.log('=== BACKEND STARTING ===');

function calculateDistance(lat1, lon1, lat2, lon2) {
  console.log(`CALC_DISTANCE(${lat1}, ${lon1}, ${lat2}, ${lon2})`);
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const result = R * c;
  console.log(`RESULT: ${result.toFixed(2)} km`);
  return result;
}

function calculateFare(distance) {
  console.log(`CALC_FARE(${distance})`);
  const fare = 1.50 + (distance * 1.25) + ((distance * 5) * 0.35);
  console.log(`FARE_RESULT: $${fare.toFixed(2)}`);
  return fare;
}

app.post('/api/v1/rides', (req, res) => {
  console.log('===== POST /api/v1/rides =====');
  const pickup = req.body.pickup_location;
  const dropoff = req.body.dropoff_location;
  
  console.log(`Input pickup: ${pickup.latitude}, ${pickup.longitude}`);
  console.log(`Input dropoff: ${dropoff.latitude}, ${dropoff.longitude}`);

  const dist = calculateDistance(pickup.latitude, pickup.longitude, dropoff.latitude, dropoff.longitude);
  const fare = calculateFare(dist);

  console.log(`Returning: distance=${dist.toFixed(2)}, fare=${fare.toFixed(2)}`);

  res.json({
    distance: Math.round(dist * 100) / 100,
    estimated_fare: Math.round(fare * 100) / 100
  });
});

app.listen(8000, () => {
  console.log('Listening on 8000');
  console.log('=== READY FOR TESTING ===\n');
});
