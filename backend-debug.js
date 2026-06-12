const express = require('express');
const cors = require('cors');
const app = express();
app.use(cors());
app.use(express.json());

app.use((req, res, next) => {
  console.log(`${req.method} ${req.path}`);
  next();
});

let rides = [];

app.post('/api/v1/rides', (req, res) => {
  const ride = {
    id: 'ride-' + Date.now(),
    distance: 5.31,
    estimated_fare: 17.44
  };
  rides.push(ride);
  console.log('Ride created:', ride.id);
  res.json(ride);
});

app.get('/api/v1/rides/available', (req, res) => {
  console.log('Returning', rides.length, 'rides');
  res.json(rides);
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

app.listen(7000, () => {
  console.log('Debug backend on :7000');
});
