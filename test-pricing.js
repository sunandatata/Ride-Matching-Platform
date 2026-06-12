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

// Test 1: NYC coordinates
const dist1 = haversine(40.7128, -74.0060, 40.7580, -73.9855);
const fare1 = calculateFare(dist1);
console.log('Test 1 (NYC): ' + dist1.toFixed(2) + ' km = $' + fare1.toFixed(2));

// Test 2: (0,0) to (1,1)
const dist2 = haversine(0, 0, 1, 1);
const fare2 = calculateFare(dist2);
console.log('Test 2 (0,0->1,1): ' + dist2.toFixed(2) + ' km = $' + fare2.toFixed(2));

// Test 3: (0,0) to (10,10)
const dist3 = haversine(0, 0, 10, 10);
const fare3 = calculateFare(dist3);
console.log('Test 3 (0,0->10,10): ' + dist3.toFixed(2) + ' km = $' + fare3.toFixed(2));
