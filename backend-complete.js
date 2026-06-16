const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(cors());
app.use(express.json({ limit: '1mb' }));

const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'portfolio-db.json');
const TOKEN_SECRET = process.env.PORTFOLIO_AUTH_SECRET || 'portfolio-demo-secret';
const ACCESS_TOKEN_TTL_MS = 1000 * 60 * 60 * 8;
const REFRESH_TOKEN_TTL_MS = 1000 * 60 * 60 * 24 * 14;

const fallbackLocations = {
  pickup: { latitude: 0, longitude: 0, address: 'Unknown pickup location' },
  dropoff: { latitude: 0, longitude: 0, address: 'Unknown dropoff location' }
};

const lifecycleOrder = [
  'requested',
  'driver_assigned',
  'driver_accepted',
  'driver_arriving',
  'driver_arrived',
  'trip_started',
  'in_progress',
  'completed'
];

function nowIso() {
  return new Date().toISOString();
}

function createId(prefix) {
  return `${prefix}-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
}

function round(value, places = 2) {
  const factor = Math.pow(10, places);
  return Math.round((Number(value) || 0) * factor) / factor;
}

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
}

function createEmptyDb() {
  return {
    schema_version: 1,
    users: [],
    vehicles: [],
    rides: [],
    sessions: [],
    payment_methods: [],
    documents: []
  };
}

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.scryptSync(String(password), salt, 64).toString('hex');
  return `${salt}:${hash}`;
}

function verifyPassword(password, storedHash) {
  if (!storedHash || !storedHash.includes(':')) return false;
  const [salt, hash] = storedHash.split(':');
  const testHash = crypto.scryptSync(String(password), salt, 64);
  const storedBuffer = Buffer.from(hash, 'hex');
  return storedBuffer.length === testHash.length && crypto.timingSafeEqual(storedBuffer, testHash);
}

function seedUser(db, user) {
  const existing = db.users.find((entry) => entry.email === user.email || entry.phone === user.phone);
  if (existing) return existing;

  const timestamp = nowIso();
  const seeded = {
    id: user.id,
    role: user.role,
    name: user.name,
    email: user.email,
    phone: user.phone,
    password_hash: hashPassword(user.password),
    verified: true,
    status: user.role === 'driver' ? 'active' : 'active',
    approval_status: user.role === 'driver' ? 'approved' : undefined,
    document_verified: user.role === 'driver' ? true : undefined,
    rating: user.role === 'driver' ? 4.8 : undefined,
    created_at: timestamp,
    updated_at: timestamp
  };
  db.users.push(seeded);
  return seeded;
}

function seedDatabase(db) {
  const admin = seedUser(db, {
    id: 'demo-admin-001',
    role: 'admin',
    name: 'Demo Admin',
    email: 'admin@rideshare.com',
    phone: '+1234567890',
    password: 'admin123'
  });

  const rider = seedUser(db, {
    id: 'demo-rider-001',
    role: 'rider',
    name: 'Demo Rider',
    email: 'rider@rideshare.com',
    phone: '+1987654321',
    password: 'password123'
  });

  const driver = seedUser(db, {
    id: 'demo-driver-001',
    role: 'driver',
    name: 'Demo Driver',
    email: 'driver@rideshare.com',
    phone: '+1987654322',
    password: 'password123'
  });

  if (!db.vehicles.find((vehicle) => vehicle.driver_id === driver.id)) {
    db.vehicles.push({
      id: 'vehicle-demo-driver-001',
      driver_id: driver.id,
      license_plate: 'ABC-1234',
      make: 'Toyota',
      model: 'Prius',
      year: 2022,
      color: 'Silver',
      vehicle_type: 'Hybrid',
      capacity: 4,
      insurance_expiry: '2026-12-31',
      verified: true,
      created_at: nowIso(),
      updated_at: nowIso()
    });
  }

  return Boolean(admin && rider && driver);
}

function loadDb() {
  ensureDataDir();
  if (!fs.existsSync(DB_FILE)) {
    const db = createEmptyDb();
    seedDatabase(db);
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
    return db;
  }

  const db = { ...createEmptyDb(), ...JSON.parse(fs.readFileSync(DB_FILE, 'utf8')) };
  seedDatabase(db);
  saveDb(db);
  return db;
}

function saveDb(nextDb = db) {
  ensureDataDir();
  fs.writeFileSync(DB_FILE, JSON.stringify(nextDb, null, 2));
}

let db = loadDb();

function base64url(input) {
  return Buffer.from(input).toString('base64url');
}

function signToken(payload, ttlMs, type) {
  const body = {
    ...payload,
    type,
    iat: Date.now(),
    exp: Date.now() + ttlMs,
    jti: crypto.randomBytes(12).toString('hex')
  };
  const encoded = base64url(JSON.stringify(body));
  const signature = crypto.createHmac('sha256', TOKEN_SECRET).update(encoded).digest('base64url');
  return `portfolio.${encoded}.${signature}`;
}

function verifyToken(token, expectedType = 'access') {
  if (!token || !token.startsWith('portfolio.')) return null;
  const [, encoded, signature] = token.split('.');
  const expected = crypto.createHmac('sha256', TOKEN_SECRET).update(encoded).digest('base64url');
  if (signature !== expected) return null;

  const payload = JSON.parse(Buffer.from(encoded, 'base64url').toString('utf8'));
  if (payload.exp < Date.now()) return null;
  if (expectedType && payload.type !== expectedType) return null;
  return payload;
}

function publicUser(user) {
  if (!user) return null;
  return {
    id: user.id,
    role: user.role,
    name: user.name,
    email: user.email,
    phone: user.phone,
    verified: Boolean(user.verified),
    status: user.status,
    approval_status: user.approval_status,
    document_verified: user.document_verified
  };
}

function getRoleFromRequest(req, token = '') {
  const requestedRole = String(req.body?.role || req.query?.role || '').toLowerCase();
  if (['admin', 'driver', 'rider'].includes(requestedRole)) return requestedRole;

  const tokenAndBody = `${token} ${req.body?.refresh_token || ''}`.toLowerCase();
  if (tokenAndBody.includes('admin')) return 'admin';
  if (tokenAndBody.includes('driver')) return 'driver';
  if (tokenAndBody.includes('rider')) return 'rider';

  const origin = `${req.get('origin') || ''} ${req.get('referer') || ''}`.toLowerCase();
  if (origin.includes(':3000') || origin.includes(':5173')) return 'admin';
  if (origin.includes(':3002') || origin.includes(':5175')) return 'driver';
  if (origin.includes(':3001') || origin.includes(':5174')) return 'rider';

  if (req.path.startsWith('/api/v1/admin')) return 'admin';
  if (req.path.startsWith('/api/v1/drivers')) return 'driver';
  if (/\/(available|accept|arrived|start|complete)$/.test(req.path)) return 'driver';
  return 'rider';
}

function findDemoUser(role) {
  return db.users.find((user) => user.role === role && user.id.startsWith('demo-'));
}

function issueAuthResponse(user) {
  const accessToken = signToken({ sub: user.id, role: user.role }, ACCESS_TOKEN_TTL_MS, 'access');
  const refreshToken = signToken({ sub: user.id, role: user.role }, REFRESH_TOKEN_TTL_MS, 'refresh');
  db.sessions = db.sessions.filter((session) => session.user_id !== user.id || session.expires_at > Date.now());
  db.sessions.push({
    id: createId('session'),
    user_id: user.id,
    refresh_token_hash: crypto.createHash('sha256').update(refreshToken).digest('hex'),
    expires_at: Date.now() + REFRESH_TOKEN_TTL_MS,
    created_at: nowIso()
  });
  saveDb();
  return {
    access_token: accessToken,
    refresh_token: refreshToken,
    user: publicUser(user)
  };
}

function auth(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];

  if (token?.startsWith('demo')) {
    const role = getRoleFromRequest(req, token);
    const user = findDemoUser(role);
    if (!user) return res.status(401).json({ error: 'Unauthorized' });
    req.user = user;
    return next();
  }

  const payload = verifyToken(token, 'access');
  if (!payload) return res.status(401).json({ error: 'Unauthorized' });

  const user = db.users.find((entry) => entry.id === payload.sub);
  if (!user) return res.status(401).json({ error: 'Unauthorized' });
  req.user = user;
  return next();
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!roles.includes(req.user.role)) return res.status(403).json({ error: 'Forbidden' });
    return next();
  };
}

function normalizeString(value) {
  return String(value || '').trim();
}

function normalizeEmail(value) {
  return normalizeString(value).toLowerCase();
}

function isFiniteCoordinate(value) {
  const number = Number(value);
  return Number.isFinite(number);
}

function normalizeLocation(location, fallback) {
  const address = normalizeString(location?.address || fallback.address);
  const hasCoordinates = isFiniteCoordinate(location?.latitude) && isFiniteCoordinate(location?.longitude);

  if (hasCoordinates) {
    return {
      latitude: round(Number(location.latitude), 6),
      longitude: round(Number(location.longitude), 6),
      address: address || fallback.address
    };
  }

  return {
    latitude: round(fallback.latitude, 6),
    longitude: round(fallback.longitude, 6),
    address: address || fallback.address
  };
}

function calculateDistance(lat1, lon1, lat2, lon2) {
  const radiusKm = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
    + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180)
    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return radiusKm * c;
}

function estimateRide(pickup, dropoff) {
  const distance = calculateDistance(
    pickup.latitude,
    pickup.longitude,
    dropoff.latitude,
    dropoff.longitude
  );
  const etaMinutes = Math.max(4, Math.ceil(distance * 4 + 3));
  const fare = Math.max(6, 3.5 + (distance * 1.45) + (etaMinutes * 0.4));
  return {
    distance: round(distance),
    estimated_fare: round(fare),
    eta_seconds: etaMinutes * 60
  };
}

function activeRideStatuses() {
  return ['requested', 'driver_assigned', 'driver_accepted', 'driver_arriving', 'driver_arrived', 'trip_started', 'in_progress'];
}

function assignLifecycle(ride, statuses) {
  const timestamp = nowIso();
  ride.lifecycle = ride.lifecycle || {};
  statuses.forEach((status) => {
    if (!ride.lifecycle[status]) ride.lifecycle[status] = timestamp;
  });
  ride.updated_at = timestamp;
}

function setRideStatus(ride, status) {
  if (status === 'driver_arriving') {
    assignLifecycle(ride, ['driver_assigned', 'driver_accepted', 'driver_arriving']);
  } else if (status === 'driver_arrived') {
    assignLifecycle(ride, ['driver_arrived']);
  } else if (status === 'in_progress') {
    assignLifecycle(ride, ['trip_started', 'in_progress']);
  } else {
    assignLifecycle(ride, [status]);
  }
  ride.status = status;
  if (status === 'completed') ride.completed_at = ride.lifecycle.completed;
}

function serializeRide(ride) {
  const driver = ride.driver_id ? db.users.find((user) => user.id === ride.driver_id) : null;
  const vehicle = ride.driver_id ? db.vehicles.find((entry) => entry.driver_id === ride.driver_id) : null;
  return {
    ...ride,
    driver_name: driver?.name || ride.driver_name,
    driver_rating: driver?.rating || ride.driver_rating || 4.8,
    vehicle: vehicle ? `${vehicle.make} ${vehicle.model} (${vehicle.color})` : ride.vehicle,
    vehicle_details: vehicle || null,
    license_plate: vehicle?.license_plate || ride.license_plate,
    timeline: lifecycleOrder.map((key) => ({
      key,
      completed: Boolean(ride.lifecycle?.[key]),
      completed_at: ride.lifecycle?.[key] || null
    }))
  };
}

function paginate(items, page = 1, perPage = 10) {
  const safePage = Math.max(Number(page) || 1, 1);
  const safePerPage = Math.max(Number(perPage) || 10, 1);
  const start = (safePage - 1) * safePerPage;
  return {
    data: items.slice(start, start + safePerPage),
    total: items.length,
    page: safePage,
    per_page: safePerPage,
    total_pages: Math.max(Math.ceil(items.length / safePerPage), 1)
  };
}

function getDriverCompletedRides(driverId) {
  return db.rides
    .filter((ride) => ride.driver_id === driverId && ride.status === 'completed')
    .sort((a, b) => new Date(b.completed_at || b.updated_at).getTime() - new Date(a.completed_at || a.updated_at).getTime());
}

function calculateDriverEarning(ride) {
  return round(ride.driver_earning ?? ((ride.actual_fare || ride.estimated_fare) * 0.85));
}

function summarizeDriverEarnings(driverId) {
  const rides = getDriverCompletedRides(driverId);
  return rides.reduce(
    (totals, ride) => {
      const earning = calculateDriverEarning(ride);
      return {
        total: round(totals.total + earning),
        daily: round(totals.daily + earning),
        weekly: round(totals.weekly + earning),
        rideCount: totals.rideCount + 1
      };
    },
    { total: 0, daily: 0, weekly: 0, rideCount: 0 }
  );
}

function driverSummary(user) {
  const completed = getDriverCompletedRides(user.id);
  const earnings = summarizeDriverEarnings(user.id);
  return {
    id: user.id,
    user_id: user.id,
    name: user.name,
    phone: user.phone,
    email: user.email,
    vehicle_id: db.vehicles.find((vehicle) => vehicle.driver_id === user.id)?.id,
    status: user.status || 'active',
    approval_status: user.approval_status || 'pending',
    rating: user.rating || 4.8,
    total_rides: completed.length,
    total_earnings: earnings.total,
    document_verified: Boolean(user.document_verified),
    created_at: user.created_at,
    updated_at: user.updated_at
  };
}

function todayRides() {
  const today = new Date().toISOString().slice(0, 10);
  return db.rides.filter((ride) => ride.created_at.slice(0, 10) === today);
}

function completedRideRevenue(rides) {
  return round(rides
    .filter((ride) => ride.status === 'completed')
    .reduce((sum, ride) => sum + Number(ride.actual_fare || ride.estimated_fare || 0), 0));
}

function averageRating(rides) {
  const rated = rides.filter((ride) => Number(ride.rating) > 0);
  if (!rated.length) return 0;
  return round(rated.reduce((sum, ride) => sum + Number(ride.rating), 0) / rated.length);
}

function chartRows(period, key) {
  const days = period === 'day' ? 7 : period === 'week' ? 8 : 12;
  const rows = [];
  for (let index = days - 1; index >= 0; index -= 1) {
    const date = new Date();
    date.setDate(date.getDate() - index);
    const label = date.toISOString().slice(0, 10);
    const rides = db.rides.filter((ride) => ride.created_at.slice(0, 10) === label);
    rows.push({
      date: label,
      rides: rides.length,
      revenue: completedRideRevenue(rides)
    });
  }
  return rows.map((row) => ({ date: row.date, [key]: row[key] }));
}

app.post('/api/v1/auth/register', (req, res) => {
  const role = getRoleFromRequest(req);
  if (!['rider', 'driver'].includes(role)) return res.status(400).json({ error: 'Invalid registration role' });

  const name = normalizeString(req.body.name);
  const email = normalizeEmail(req.body.email);
  const phone = normalizeString(req.body.phone);
  const password = normalizeString(req.body.password);

  if (!name || !email || !phone || !password) {
    return res.status(400).json({ error: 'Name, email, phone, and password are required' });
  }

  const duplicate = db.users.find((user) => user.email === email || user.phone === phone);
  if (duplicate) return res.status(409).json({ error: 'An account already exists for this email or phone' });

  const timestamp = nowIso();
  const user = {
    id: createId(role),
    role,
    name,
    email,
    phone,
    password_hash: hashPassword(password),
    verified: true,
    status: 'active',
    approval_status: role === 'driver' ? 'approved' : undefined,
    document_verified: role === 'driver',
    rating: role === 'driver' ? 4.9 : undefined,
    created_at: timestamp,
    updated_at: timestamp
  };
  db.users.push(user);

  if (role === 'driver') {
    const vehicle = req.body.vehicle || req.body.vehicle_info || {};
    db.vehicles.push({
      id: createId('vehicle'),
      driver_id: user.id,
      license_plate: normalizeString(vehicle.license_plate || vehicle.licensePlate),
      make: normalizeString(vehicle.make || 'Toyota'),
      model: normalizeString(vehicle.model || 'Prius'),
      year: Number(vehicle.year) || 2023,
      color: normalizeString(vehicle.color || 'Black'),
      vehicle_type: normalizeString(vehicle.vehicle_type || vehicle.type || 'Sedan'),
      capacity: Number(vehicle.capacity) || 4,
      insurance_expiry: normalizeString(vehicle.insurance_expiry || '2027-12-31'),
      verified: true,
      created_at: timestamp,
      updated_at: timestamp
    });
  }

  saveDb();
  return res.status(201).json(issueAuthResponse(user));
});

app.post('/api/v1/auth/login', (req, res) => {
  const role = getRoleFromRequest(req);
  const identifier = normalizeEmail(req.body.email || req.body.phone);
  const phone = normalizeString(req.body.phone);
  const password = normalizeString(req.body.password);

  const user = db.users.find((entry) => {
    const matchesRole = entry.role === role;
    const matchesEmail = entry.email === identifier;
    const matchesPhone = entry.phone === phone || entry.phone === identifier;
    return matchesRole && (matchesEmail || matchesPhone);
  });

  if (!user || !verifyPassword(password, user.password_hash)) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }

  return res.json(issueAuthResponse(user));
});

app.post('/api/v1/auth/refresh', (req, res) => {
  const refreshToken = req.body.refresh_token;

  if (refreshToken?.startsWith('demo')) {
    const role = getRoleFromRequest(req, refreshToken);
    const user = findDemoUser(role);
    if (!user) return res.status(401).json({ error: 'Invalid refresh token' });
    return res.json(issueAuthResponse(user));
  }

  const payload = verifyToken(refreshToken, 'refresh');
  if (!payload) return res.status(401).json({ error: 'Invalid refresh token' });

  const tokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');
  const session = db.sessions.find((entry) => entry.user_id === payload.sub && entry.refresh_token_hash === tokenHash && entry.expires_at > Date.now());
  const user = db.users.find((entry) => entry.id === payload.sub);
  if (!session || !user) return res.status(401).json({ error: 'Invalid refresh token' });

  return res.json(issueAuthResponse(user));
});

app.post('/api/v1/auth/logout', auth, (req, res) => {
  db.sessions = db.sessions.filter((session) => session.user_id !== req.user.id);
  saveDb();
  res.status(204).send();
});

app.get('/api/v1/auth/validate', auth, (req, res) => {
  res.json({ valid: true, user: publicUser(req.user) });
});

app.get('/api/v1/riders/profile', auth, requireRole('rider'), (req, res) => {
  res.json(publicUser(req.user));
});

app.put('/api/v1/riders/profile', auth, requireRole('rider'), (req, res) => {
  req.user.name = normalizeString(req.body.name || req.user.name);
  req.user.email = normalizeEmail(req.body.email || req.user.email);
  req.user.phone = normalizeString(req.body.phone || req.user.phone);
  req.user.updated_at = nowIso();
  saveDb();
  res.json(publicUser(req.user));
});

app.get('/api/v1/riders/payment-methods', auth, requireRole('rider'), (req, res) => {
  res.json(db.payment_methods.filter((method) => method.rider_id === req.user.id));
});

app.post('/api/v1/riders/payment-methods', auth, requireRole('rider'), (req, res) => {
  const method = {
    id: createId('payment'),
    rider_id: req.user.id,
    type: req.body.type || 'credit',
    last4: req.body.last4 || String(req.body.card_number || '').slice(-4),
    default: !db.payment_methods.some((entry) => entry.rider_id === req.user.id),
    created_at: nowIso()
  };
  db.payment_methods.push(method);
  saveDb();
  res.status(201).json(method);
});

app.delete('/api/v1/riders/payment-methods/:methodId', auth, requireRole('rider'), (req, res) => {
  db.payment_methods = db.payment_methods.filter((method) => method.id !== req.params.methodId || method.rider_id !== req.user.id);
  saveDb();
  res.status(204).send();
});

app.post('/api/v1/rides', auth, requireRole('rider'), (req, res) => {
  const pickup = normalizeLocation(req.body.pickup_location, fallbackLocations.pickup);
  const dropoff = normalizeLocation(req.body.dropoff_location, fallbackLocations.dropoff);
  const estimate = estimateRide(pickup, dropoff);
  const timestamp = nowIso();

  db.rides
    .filter((ride) => ride.rider_id === req.user.id && ride.status === 'requested')
    .forEach((ride) => {
      ride.status = 'cancelled';
      ride.cancel_reason = 'Superseded by a new request';
      ride.updated_at = timestamp;
    });

  const ride = {
    id: createId('ride'),
    rider_id: req.user.id,
    rider_name: req.user.name,
    status: 'requested',
    lifecycle: { requested: timestamp },
    pickup_location: pickup,
    dropoff_location: dropoff,
    distance: estimate.distance,
    estimated_fare: estimate.estimated_fare,
    eta_seconds: estimate.eta_seconds,
    created_at: timestamp,
    updated_at: timestamp
  };

  db.rides.push(ride);
  saveDb();
  return res.status(201).json(serializeRide(ride));
});

app.get('/api/v1/rides/available', auth, requireRole('driver'), (_req, res) => {
  const rides = db.rides
    .filter((ride) => ride.status === 'requested')
    .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime())
    .map(serializeRide);
  res.json(rides);
});

app.get('/api/v1/rides/current', auth, (req, res) => {
  let ride = null;
  if (req.user.role === 'rider') {
    ride = db.rides
      .filter((entry) => entry.rider_id === req.user.id && !['cancelled'].includes(entry.status))
      .sort((a, b) => new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime())[0] || null;
  } else if (req.user.role === 'driver') {
    ride = db.rides
      .filter((entry) => entry.driver_id === req.user.id && activeRideStatuses().includes(entry.status))
      .sort((a, b) => new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime())[0] || null;
  }
  res.json(ride ? serializeRide(ride) : null);
});

app.get('/api/v1/rides', auth, (req, res) => {
  let rides = db.rides;
  if (req.user.role === 'rider') rides = rides.filter((ride) => ride.rider_id === req.user.id);
  if (req.user.role === 'driver') rides = rides.filter((ride) => ride.driver_id === req.user.id);
  if (req.query.status) rides = rides.filter((ride) => ride.status === req.query.status);
  rides = rides.sort((a, b) => new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime()).map(serializeRide);
  res.json(paginate(rides, req.query.page, req.query.per_page));
});

app.get('/api/v1/rides/:rideId', auth, (req, res) => {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId);
  if (!ride) return res.status(404).json({ error: 'Ride not found' });
  res.json(serializeRide(ride));
});

app.post('/api/v1/rides/:rideId/accept', auth, requireRole('driver'), (req, res) => {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId && entry.status === 'requested');
  if (!ride) return res.status(404).json({ error: 'Ride not found' });

  ride.driver_id = req.user.id;
  ride.driver_name = req.user.name;
  ride.driver_rating = req.user.rating || 4.8;
  const vehicle = db.vehicles.find((entry) => entry.driver_id === req.user.id);
  if (vehicle) {
    ride.vehicle = `${vehicle.make} ${vehicle.model} (${vehicle.color})`;
    ride.license_plate = vehicle.license_plate;
  }
  setRideStatus(ride, 'driver_arriving');
  saveDb();
  res.json(serializeRide(ride));
});

app.post('/api/v1/rides/:rideId/arrived', auth, requireRole('driver'), (req, res) => {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId && entry.driver_id === req.user.id);
  if (!ride) return res.status(404).json({ error: 'Ride not found' });
  setRideStatus(ride, 'driver_arrived');
  saveDb();
  res.json(serializeRide(ride));
});

app.post('/api/v1/rides/:rideId/start', auth, requireRole('driver'), (req, res) => {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId && entry.driver_id === req.user.id);
  if (!ride) return res.status(404).json({ error: 'Ride not found' });
  setRideStatus(ride, 'in_progress');
  saveDb();
  res.json(serializeRide(ride));
});

app.post('/api/v1/rides/:rideId/complete', auth, requireRole('driver'), (req, res) => {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId && entry.driver_id === req.user.id);
  if (!ride) return res.status(404).json({ error: 'Ride not found' });

  const actualFare = round(req.body.fare || ride.estimated_fare);
  ride.actual_fare = actualFare;
  ride.driver_earning = round(actualFare * 0.85);
  setRideStatus(ride, 'completed');
  saveDb();
  res.json(serializeRide(ride));
});

app.post('/api/v1/rides/:rideId/cancel', auth, (req, res) => {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId);
  if (!ride) return res.status(404).json({ error: 'Ride not found' });

  ride.status = 'cancelled';
  ride.cancel_reason = req.body.reason || 'Cancelled';
  ride.updated_at = nowIso();
  saveDb();
  res.json(serializeRide(ride));
});

function handleRateRide(req, res) {
  const ride = db.rides.find((entry) => entry.id === req.params.rideId && entry.rider_id === req.user.id);
  if (!ride) return res.status(404).json({ error: 'Ride not found' });

  ride.rating = Number(req.body.rating) || 5;
  ride.feedback = req.body.feedback || '';
  ride.updated_at = nowIso();
  saveDb();
  res.json(serializeRide(ride));
}

app.post('/api/v1/rides/:rideId/rate', auth, requireRole('rider'), handleRateRide);
app.post('/api/v1/rides/:rideId/rating', auth, requireRole('rider'), handleRateRide);

app.get('/api/v1/drivers/rides', auth, requireRole('driver'), (req, res) => {
  const rides = db.rides
    .filter((ride) => ride.driver_id === req.user.id)
    .sort((a, b) => new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime())
    .map(serializeRide);
  res.json(paginate(rides, req.query.page, req.query.per_page));
});

app.get('/api/v1/drivers/earnings', auth, requireRole('driver'), (req, res) => {
  res.json(summarizeDriverEarnings(req.user.id));
});

app.get('/api/v1/drivers/earnings/history', auth, requireRole('driver'), (req, res) => {
  const history = getDriverCompletedRides(req.user.id).map((ride) => ({
    id: ride.id,
    date: ride.completed_at || ride.updated_at,
    distance: ride.distance,
    fare: ride.actual_fare || ride.estimated_fare,
    earning: calculateDriverEarning(ride)
  }));
  res.json({
    data: history,
    total: history.length,
    totalEarnings: round(history.reduce((sum, item) => sum + item.earning, 0))
  });
});

app.get('/api/v1/drivers/stats', auth, requireRole('driver'), (req, res) => {
  const completed = getDriverCompletedRides(req.user.id);
  res.json({
    total_rides: db.rides.filter((ride) => ride.driver_id === req.user.id).length,
    completed_rides: completed.length,
    rating: req.user.rating || 4.8,
    total_earnings: summarizeDriverEarnings(req.user.id).total,
    cancellation_rate: 0
  });
});

app.get('/api/v1/drivers/vehicle', auth, requireRole('driver'), (req, res) => {
  const vehicle = db.vehicles.find((entry) => entry.driver_id === req.user.id);
  if (!vehicle) return res.status(404).json({ error: 'Vehicle not found' });
  res.json(vehicle);
});

app.put('/api/v1/drivers/vehicle', auth, requireRole('driver'), (req, res) => {
  let vehicle = db.vehicles.find((entry) => entry.driver_id === req.user.id);
  if (!vehicle) {
    vehicle = { id: createId('vehicle'), driver_id: req.user.id, created_at: nowIso() };
    db.vehicles.push(vehicle);
  }
  Object.assign(vehicle, req.body, { updated_at: nowIso(), verified: true });
  saveDb();
  res.json(vehicle);
});

app.get('/api/v1/drivers/documents', auth, requireRole('driver'), (req, res) => {
  res.json(db.documents.filter((document) => document.driver_id === req.user.id));
});

app.post('/api/v1/drivers/tracking/start', auth, requireRole('driver'), (req, res) => {
  req.user.status = 'active';
  req.user.updated_at = nowIso();
  saveDb();
  res.status(204).send();
});

app.post('/api/v1/drivers/tracking/stop', auth, requireRole('driver'), (req, res) => {
  req.user.status = 'inactive';
  req.user.updated_at = nowIso();
  saveDb();
  res.status(204).send();
});

app.post('/api/v1/locations/update', auth, requireRole('driver'), (req, res) => {
  req.user.last_location = {
    latitude: Number(req.body.latitude),
    longitude: Number(req.body.longitude),
    timestamp: req.body.timestamp || nowIso()
  };
  req.user.updated_at = nowIso();
  saveDb();
  res.status(204).send();
});

app.get('/api/v1/drivers', auth, requireRole('admin'), (req, res) => {
  let drivers = db.users.filter((user) => user.role === 'driver').map(driverSummary);
  if (req.query.status) drivers = drivers.filter((driver) => driver.status === req.query.status);
  if (req.query.approval_status) drivers = drivers.filter((driver) => driver.approval_status === req.query.approval_status);
  res.json(paginate(drivers, req.query.page, req.query.per_page));
});

app.get('/api/v1/drivers/:driverId', auth, requireRole('admin'), (req, res) => {
  const user = db.users.find((entry) => entry.id === req.params.driverId && entry.role === 'driver');
  if (!user) return res.status(404).json({ error: 'Driver not found' });
  res.json(driverSummary(user));
});

app.put('/api/v1/drivers/:driverId/approval', auth, requireRole('admin'), (req, res) => {
  const user = db.users.find((entry) => entry.id === req.params.driverId && entry.role === 'driver');
  if (!user) return res.status(404).json({ error: 'Driver not found' });
  user.approval_status = req.body.status || 'approved';
  user.updated_at = nowIso();
  saveDb();
  res.json(driverSummary(user));
});

app.put('/api/v1/drivers/:driverId/suspension', auth, requireRole('admin'), (req, res) => {
  const user = db.users.find((entry) => entry.id === req.params.driverId && entry.role === 'driver');
  if (!user) return res.status(404).json({ error: 'Driver not found' });
  user.status = req.body.status || 'suspended';
  user.suspension_reason = req.body.reason || '';
  user.updated_at = nowIso();
  saveDb();
  res.json(driverSummary(user));
});

app.get('/api/v1/drivers/:driverId/vehicle', auth, requireRole('admin'), (req, res) => {
  const vehicle = db.vehicles.find((entry) => entry.driver_id === req.params.driverId);
  if (!vehicle) return res.status(404).json({ error: 'Vehicle not found' });
  res.json(vehicle);
});

app.put('/api/v1/drivers/:driverId/vehicle', auth, requireRole('admin'), (req, res) => {
  let vehicle = db.vehicles.find((entry) => entry.driver_id === req.params.driverId);
  if (!vehicle) {
    vehicle = { id: createId('vehicle'), driver_id: req.params.driverId, created_at: nowIso() };
    db.vehicles.push(vehicle);
  }
  Object.assign(vehicle, req.body, { updated_at: nowIso() });
  saveDb();
  res.json(vehicle);
});

app.get('/api/v1/admin/metrics', auth, requireRole('admin'), (_req, res) => {
  const drivers = db.users.filter((user) => user.role === 'driver');
  const riders = db.users.filter((user) => user.role === 'rider');
  const ridesToday = todayRides();
  res.json({
    active_drivers: drivers.filter((driver) => driver.status === 'active').length,
    active_riders: riders.length,
    total_rides_today: ridesToday.length,
    total_revenue_today: completedRideRevenue(ridesToday),
    pending_approvals: drivers.filter((driver) => driver.approval_status === 'pending').length,
    average_ride_rating: averageRating(db.rides),
    system_health: {
      api_status: 'healthy',
      database_status: 'healthy',
      queue_length: db.rides.filter((ride) => ride.status === 'requested').length
    }
  });
});

app.get('/api/v1/admin/rides/stats', auth, requireRole('admin'), (_req, res) => {
  res.json({
    total_rides: db.rides.length,
    completed_rides: db.rides.filter((ride) => ride.status === 'completed').length,
    cancelled_rides: db.rides.filter((ride) => ride.status === 'cancelled').length,
    total_revenue: completedRideRevenue(db.rides),
    average_rating: averageRating(db.rides)
  });
});

app.get('/api/v1/admin/drivers/stats', auth, requireRole('admin'), (_req, res) => {
  const drivers = db.users.filter((user) => user.role === 'driver');
  res.json({
    total_drivers: drivers.length,
    active_drivers: drivers.filter((driver) => driver.status === 'active').length,
    pending_approvals: drivers.filter((driver) => driver.approval_status === 'pending').length,
    suspended_drivers: drivers.filter((driver) => driver.status === 'suspended').length,
    average_rating: drivers.length ? round(drivers.reduce((sum, driver) => sum + Number(driver.rating || 0), 0) / drivers.length) : 0
  });
});

app.get('/api/v1/admin/analytics/revenue', auth, requireRole('admin'), (req, res) => {
  res.json(chartRows(req.query.period || 'month', 'revenue'));
});

app.get('/api/v1/admin/analytics/rides', auth, requireRole('admin'), (req, res) => {
  res.json(chartRows(req.query.period || 'month', 'rides'));
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', database: fs.existsSync(DB_FILE) ? 'ok' : 'missing' });
});

app.use((req, res) => {
  res.status(404).json({ error: 'Not found', path: req.path });
});

const PORT = 7000;
app.listen(PORT, () => {
  console.log(`Backend API running on http://localhost:${PORT}`);
  console.log(`Portfolio database: ${DB_FILE}`);
});
