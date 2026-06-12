// Simple shared storage server for demo mode
// Both rider and driver apps can access this to share ride data
const http = require('http');
const url = require('url');

let sharedData = {
  'available_rides': [],
  'current_ride': null,
};

const server = http.createServer((req, res) => {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  res.setHeader('Content-Type', 'application/json');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  // GET /api/shared/available-rides
  if (req.method === 'GET' && pathname === '/api/shared/available-rides') {
    res.writeHead(200);
    res.end(JSON.stringify(sharedData['available_rides']));
    return;
  }

  // POST /api/shared/available-rides
  if (req.method === 'POST' && pathname === '/api/shared/available-rides') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const ride = JSON.parse(body);
        sharedData['available_rides'].push(ride);
        console.log(`✓ Ride added to available: ${ride.id}`);
        res.writeHead(200);
        res.end(JSON.stringify(ride));
      } catch (e) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'Invalid JSON' }));
      }
    });
    return;
  }

  // DELETE /api/shared/available-rides/:id
  if (req.method === 'DELETE' && pathname.startsWith('/api/shared/available-rides/')) {
    const rideId = pathname.split('/').pop();
    sharedData['available_rides'] = sharedData['available_rides'].filter(r => r.id !== rideId);
    console.log(`✓ Ride removed from available: ${rideId}`);
    res.writeHead(200);
    res.end(JSON.stringify({ success: true }));
    return;
  }

  // GET /api/shared/current-ride
  if (req.method === 'GET' && pathname === '/api/shared/current-ride') {
    res.writeHead(200);
    res.end(JSON.stringify(sharedData['current_ride']));
    return;
  }

  // POST /api/shared/current-ride
  if (req.method === 'POST' && pathname === '/api/shared/current-ride') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const ride = JSON.parse(body);
        sharedData['current_ride'] = ride;
        console.log(`✓ Current ride set: ${ride.id}`);
        res.writeHead(200);
        res.end(JSON.stringify(ride));
      } catch (e) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'Invalid JSON' }));
      }
    });
    return;
  }

  // DELETE /api/shared/current-ride
  if (req.method === 'DELETE' && pathname === '/api/shared/current-ride') {
    sharedData['current_ride'] = null;
    console.log('✓ Current ride cleared');
    res.writeHead(200);
    res.end(JSON.stringify({ success: true }));
    return;
  }

  res.writeHead(404);
  res.end(JSON.stringify({ error: 'Not found' }));
});

const PORT = 3001;
server.listen(PORT, () => {
  console.log(`🌐 Shared Storage Server running on http://localhost:${PORT}`);
  console.log('   Both rider and driver apps can use this to share ride data');
});
