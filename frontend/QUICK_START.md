# Quick Start Guide

## Prerequisites
- Node.js 16+ and npm/yarn
- Backend API running at http://localhost:8000

## Installation (30 seconds)

```bash
# Admin App
cd admin-app && npm install && npm run dev
# Opens: http://localhost:3000

# Rider App (new terminal)
cd rider-app && npm install && npm run dev
# Opens: http://localhost:3001

# Driver App (new terminal)
cd driver-app && npm install && npm run dev
# Opens: http://localhost:3002
```

## Test Credentials

Use any valid phone number and password to login. Backend will validate.

Example:
- Phone: +1234567890
- Password: password123

## Project Structure

Each app is self-contained:
- **admin-app/** - Admin dashboard with sidebar navigation
- **rider-app/** - Mobile-optimized rider experience
- **driver-app/** - Mobile-optimized driver experience

## Key Features

### Admin Dashboard (port 3000)
- Real-time metrics and KPIs
- Driver approval/rejection workflow
- Ride monitoring and management
- Analytics with charts
- Responsive - works on all devices

### Rider App (port 3001)
- Request rides with location
- Track ride in real-time
- Rate drivers and leave feedback
- Manage payment methods
- View ride history

### Driver App (port 3002)
- Toggle online/offline status
- Accept ride requests
- Track earnings daily/weekly/monthly
- Manage vehicle information
- Upload required documents

## API Integration

All apps connect to backend at `/api/v1/`:

**Authentication**
```
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

**Rides**
```
GET/POST /api/v1/rides
PUT /api/v1/rides/{id}/status
POST /api/v1/rides/{id}/cancel
POST /api/v1/rides/{id}/rate
```

**Drivers**
```
GET /api/v1/drivers
GET /api/v1/drivers/{id}
PUT /api/v1/drivers/{id}
POST /api/v1/drivers/{id}/documents
```

**Locations**
```
POST /api/v1/locations/update
GET /api/v1/locations/nearby
```

## Development Tips

### Hot Module Replacement
Changes are instantly reflected - no page reload needed!

### TypeScript
Strict mode enabled - all types must be explicit.
Errors shown in terminal and editor.

### React Query DevTools
Admin app includes React Query visualization:
```
npm install @tanstack/react-query-devtools
```

### Environment Variables
Copy `.env.example` to `.env` and customize:
```
VITE_API_BASE_URL=http://localhost:8000
```

## Building for Production

```bash
# Each app builds independently
npm run build

# Output: dist/ folder ready to deploy
# Test with: npm run preview
```

## Troubleshooting

**Port 3000 already in use?**
```bash
lsof -ti:3000 | xargs kill
```

**CORS errors?**
Check backend proxy configuration in vite.config.ts

**Token expired?**
App auto-refreshes tokens. If still failing, clear localStorage:
```javascript
localStorage.clear()
```

**Build errors?**
```bash
rm -rf node_modules package-lock.json
npm install
npm run build
```

## File Structure Example

```
admin-app/
├── src/
│   ├── components/      # Reusable UI
│   ├── pages/          # Page components
│   ├── hooks/          # Custom hooks
│   ├── services/       # API layer
│   ├── store/          # State (Zustand)
│   ├── types/          # TypeScript interfaces
│   └── App.tsx         # Root component
├── vite.config.ts      # Build config
├── tsconfig.json       # TypeScript config
├── package.json        # Dependencies
└── index.html          # HTML entry
```

## Code Examples

### Calling an API
```typescript
import { rideService } from '@/services/rideService'

const ride = await rideService.getRide(rideId)
```

### Using a Hook
```typescript
import { useRides } from '@/hooks/useRides'

const { data: rides, isLoading } = useRides()
```

### Protected Component
```typescript
<ProtectedRoute>
  <HomePage />
</ProtectedRoute>
```

### Form Submission
```typescript
const mutation = useRequestRide()
mutation.mutate(rideData)
```

## Performance

- Vite builds in milliseconds
- React Query caches data (5-min default)
- Automatic API retry on failure
- Code splitting per route
- Production builds are ~50KB gzipped

## Next Steps

1. Verify backend is running on port 8000
2. Start frontend apps on ports 3000/3001/3002
3. Test login with phone + password
4. Explore features in each app
5. Customize based on your design

## Support

Check README.md for detailed documentation on:
- API integration
- Component architecture
- State management
- Responsive design
- Accessibility
- Security features

Happy coding!
