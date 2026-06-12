// Ultra-clean backend - testing distance calculation
const express = require('express');
const app = express();
app.use(express.json());

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

app.post('/api/v1/rides', (req, res) => {
  const p = req.body.pickup_location;
  const d = req.body.dropoff_location;

  const dist = haversine(p.latitude, p.longitude, d.latitude, d.longitude);
  const fare = 1.50 + (dist * 1.25) + ((dist * 5) * 0.35);

  console.log(`REQUEST: (${p.latitude},${p.longitude}) -> (${d.latitude},${d.longitude})`);
  console.log(`CALCULATED: ${dist.toFixed(2)}km = $${fare.toFixed(2)}`);

  res.json({
    distance: Math.round(dist * 100) / 100,
    estimated_fare: Math.round(fare * 100) / 100
  });
});

app.listen(9000, () => console.log('Server on :9000'));
