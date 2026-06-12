const http = require('http');
const url = require('url');

const rides = [];

const server = http.createServer((req, res) => {
  const pathname = url.parse(req.url).pathname;
  
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization');
  
  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }
  
  if (pathname === '/health' && req.method === 'GET') {
    res.writeHead(200);
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }
  
  if (pathname === '/api/v1/rides' && req.method === 'POST') {
    const ride = { id: 'ride-' + Date.now(), distance: 5.31, estimated_fare: 17.44 };
    rides.push(ride);
    res.writeHead(200);
    res.end(JSON.stringify(ride));
    return;
  }
  
  if (pathname === '/api/v1/rides/available' && req.method === 'GET') {
    res.writeHead(200);
    res.end(JSON.stringify(rides));
    return;
  }
  
  res.writeHead(404);
  res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(9001, () => {
  console.log('Minimal backend on :9001');
});
