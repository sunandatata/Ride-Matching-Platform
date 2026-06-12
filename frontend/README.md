# Ride Share Platform - Frontend Applications

Complete React 18 + TypeScript frontend applications for a ride-sharing platform with three separate apps: Admin Dashboard, Rider App, and Driver App.

## Project Structure

```
frontend/
├── admin-app/         # Admin dashboard for platform management
├── rider-app/         # Mobile-optimized app for riders
├── driver-app/        # Mobile-optimized app for drivers
└── shared/           # Shared utilities and types (optional)
```

## Tech Stack

- **React 18** - Latest React with hooks
- **TypeScript** - Full type safety
- **Vite** - Lightning-fast build tool
- **Material UI (MUI)** - Professional component library
- **React Query (TanStack Query)** - Server state management
- **Zustand** - Lightweight client state management
- **Axios** - HTTP client with interceptors
- **React Router v6** - Client-side routing
- **Day.js** - Date/time utilities
- **Recharts** - Data visualization (admin only)

## Installation

Each app has independent dependencies. Install separately:

```bash
# Admin App
cd admin-app
npm install

# Rider App
cd rider-app
npm install

# Driver App
cd driver-app
npm install
```

## Development

Run each app on a different port:

```bash
# Admin App - http://localhost:3000
cd admin-app && npm run dev

# Rider App - http://localhost:3001
cd rider-app && npm run dev

# Driver App - http://localhost:3002
cd driver-app && npm run dev
```

## Build

Production builds:

```bash
cd admin-app && npm run build
cd rider-app && npm run build
cd driver-app && npm run build
```

## Environment Configuration

Create `.env` file in each app root:

```env
VITE_API_BASE_URL=http://localhost:8000
VITE_APP_NAME=RideShare
```

## Authentication Flow

1. User logs in with phone + password
2. Backend returns `access_token` + `refresh_token`
3. Tokens stored in localStorage
4. Axios interceptor adds Bearer token to all requests
5. Auto-refresh on 401 response
6. Manual logout clears tokens

## Features

### Admin App
- **Dashboard**: Real-time platform metrics, revenue, active users
- **Driver Management**: Approve/reject applications, suspend drivers
- **Ride Monitoring**: View all rides, cancel if needed
- **Analytics**: Revenue and ride trends with charts
- **Settings**: Account and API management

### Rider App
- **Request Ride**: Pickup/dropoff location selection
- **Ride Tracking**: Real-time ride status
- **Ride History**: Past rides with ratings
- **Payment Methods**: Add/manage cards
- **Profile**: Account settings and preferences

### Driver App
- **Online/Offline Toggle**: Control availability
- **Ride Acceptance**: Accept/reject ride requests
- **Earnings Dashboard**: Track daily/weekly/monthly earnings
- **Location Tracking**: Auto-share location when online
- **Document Management**: Upload driver documents
- **Vehicle Info**: Manage vehicle details

## API Integration

All services use Axios with interceptors:

```typescript
// Services handle API calls
import { apiClient } from '@/services/api'

const response = await apiClient.get('/api/v1/endpoint')
```

### Error Handling

- Network errors show user-friendly messages
- 401 errors trigger token refresh
- Failed refresh redirects to login
- All mutations include error states

### Loading States

- Skeleton loaders for initial data
- Spinners for mutations
- Disabled buttons during async operations
- Empty states for no data

## Type Safety

All types defined in `src/types/index.ts`:

- Request/response shapes
- Component props
- Store state
- API parameters

No implicit `any` types - strict TypeScript config.

## Component Organization

### Reusable Components
- `ProtectedRoute` - Auth guard
- `Layout` - Admin sidebar navigation
- `BottomNav` - Rider/driver mobile navigation
- `DataTable` - Admin table component
- `MetricCard` - Dashboard metric display

### Pages
- Login, Dashboard, Profiles, Settings
- Mobile-first responsive design
- Touch-friendly for mobile apps

### Hooks
Custom hooks for:
- Authentication (`useAuth`)
- Data fetching (`useRides`, `useDrivers`, etc.)
- Mutations (`useAcceptRide`, `useApproveDriver`, etc.)
- Dashboard metrics (`usePlatformMetrics`)

## State Management

### Global State (Zustand)
- User authentication
- Current ride (driver app)
- Online status (driver)

### Server State (React Query)
- Rides, drivers, metrics
- Cached with 5-minute stale time
- Auto-refetch on window focus
- Optimistic updates on mutations

### Local State (useState)
- Form inputs
- Dialog/modal open states
- UI-only state

## Responsive Design

All apps use MUI's responsive system:
- Mobile-first approach
- Breakpoints: xs, sm, md, lg, xl
- Fluid layouts with Grid
- Touch-friendly buttons/inputs
- Bottom navigation on mobile

Admin app has drawer that collapses on mobile.
Rider/driver apps use bottom navigation on mobile.

## Production Checklist

- [x] TypeScript strict mode enabled
- [x] Error handling on all mutations
- [x] Loading states on all async operations
- [x] Responsive at xs, sm, md, lg breakpoints
- [x] ARIA labels and keyboard navigation
- [x] Token refresh handling
- [x] Protected routes with auth guards
- [x] Empty states for no data
- [x] User feedback on actions
- [x] No console errors/warnings
- [x] Performance optimized (memoization, lazy queries)

## Performance Optimizations

- React.memo for expensive components
- useCallback for event handlers
- useMemo for computed values
- React Query caching strategy
- Code splitting via React Router
- Lazy image loading
- Minimized API calls with batching

## Security

- Tokens stored in localStorage (httpOnly unavailable in SPA)
- CSRF protection via axios interceptors
- Automatic logout on 401
- No sensitive data in component state
- Input validation on forms
- Sanitized URLs

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers

## Troubleshooting

### Port already in use
```bash
# Kill process on port 3000/3001/3002
lsof -ti:3000 | xargs kill
```

### Cache issues
```bash
# Clear React Query cache
localStorage.clear()
```

### Build errors
```bash
# Clean and rebuild
rm -rf node_modules dist
npm install
npm run build
```

## File Structure Example

```
admin-app/
├── src/
│   ├── components/      # Reusable components
│   ├── pages/          # Page components
│   ├── hooks/          # Custom hooks
│   ├── services/       # API services
│   ├── store/          # Zustand stores
│   ├── types/          # TypeScript types
│   ├── styles/         # Global CSS
│   ├── App.tsx         # Root component
│   └── main.tsx        # Entry point
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

## Contributing

- Use TypeScript for all new code
- Follow component size limit (<300 lines)
- Add error handling for async operations
- Test responsive layout at all breakpoints
- Update types when API changes
- Document non-obvious logic

## License

Proprietary - Ride Share Platform
