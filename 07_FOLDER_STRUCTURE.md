# Folder Structure & Project Organization

This document specifies the directory layout for frontend, backend, and deployment configurations.

---

## High-Level Structure

```
ride-sharing-platform/
├── frontend/                          # React + TypeScript client applications
├── backend/                           # Java/Spring Boot microservices
├── infrastructure/                    # Kubernetes, Docker configs
├── docs/                              # Architecture, API documentation
└── README.md
```

---

## Frontend Directory Structure

```
frontend/
├── apps/                              # Multi-app workspace
│   ├── rider-app/                     # Rider mobile web / PWA
│   │   ├── public/
│   │   │   ├── index.html
│   │   │   ├── favicon.ico
│   │   │   └── manifest.json          # PWA manifest
│   │   ├── src/
│   │   │   ├── index.tsx
│   │   │   ├── App.tsx                # Main app component
│   │   │   ├── pages/
│   │   │   │   ├── HomePage.tsx
│   │   │   │   ├── RideRequest.tsx    # Request a ride
│   │   │   │   ├── RideActive.tsx     # Live ride tracking
│   │   │   │   ├── RideHistory.tsx
│   │   │   │   ├── Profile.tsx
│   │   │   │   └── Support.tsx
│   │   │   ├── components/
│   │   │   │   ├── Map/               # Map component (Google Maps)
│   │   │   │   │   ├── RideMap.tsx
│   │   │   │   │   └── RideMap.module.css
│   │   │   │   ├── LocationPicker/    # Pickup/dropoff selection
│   │   │   │   │   ├── LocationPicker.tsx
│   │   │   │   │   └── LocationPicker.module.css
│   │   │   │   ├── DriverCard/        # Driver info display
│   │   │   │   ├── RatingModal/
│   │   │   │   ├── PaymentSelector/
│   │   │   │   └── Navbar/
│   │   │   ├── hooks/
│   │   │   │   ├── useWebSocket.ts    # WebSocket connection management
│   │   │   │   ├── useLocation.ts     # Geolocation API wrapper
│   │   │   │   ├── useRide.ts         # Ride API calls
│   │   │   │   ├── useAuth.ts         # Authentication
│   │   │   │   └── useMap.ts          # Map control
│   │   │   ├── services/
│   │   │   │   ├── api/
│   │   │   │   │   ├── rideService.ts
│   │   │   │   │   ├── authService.ts
│   │   │   │   │   ├── userService.ts
│   │   │   │   │   └── axios.config.ts (HTTP client setup)
│   │   │   │   ├── websocket/
│   │   │   │   │   ├── WebSocketClient.ts
│   │   │   │   │   ├── MessageHandler.ts
│   │   │   │   │   └── ReconnectionManager.ts
│   │   │   │   └── geo/
│   │   │   │       └── GeolocationService.ts
│   │   │   ├── redux/                 # State management
│   │   │   │   ├── store.ts
│   │   │   │   ├── slices/
│   │   │   │   │   ├── authSlice.ts   # Auth state
│   │   │   │   │   ├── rideSlice.ts   # Current ride state
│   │   │   │   │   ├── locationSlice.ts
│   │   │   │   │   └── uiSlice.ts     # UI state (modals, etc.)
│   │   │   │   └── middleware/
│   │   │   │       └── websocketMiddleware.ts
│   │   │   ├── styles/
│   │   │   │   ├── global.css
│   │   │   │   ├── variables.css      # Color scheme, spacing
│   │   │   │   └── responsive.css     # Mobile breakpoints
│   │   │   ├── utils/
│   │   │   │   ├── formatters.ts      # Date, currency formatting
│   │   │   │   ├── validators.ts      # Form validation
│   │   │   │   ├── errorHandling.ts
│   │   │   │   └── storage.ts         # localStorage wrapper
│   │   │   ├── types/
│   │   │   │   ├── api.types.ts       # API response types
│   │   │   │   ├── domain.types.ts    # Domain entities (Ride, Driver, etc.)
│   │   │   │   └── websocket.types.ts # WebSocket message types
│   │   │   └── constants/
│   │   │       ├── api.ts
│   │   │       ├── map.ts             # Map config (zoom levels, etc.)
│   │   │       └── errors.ts
│   │   ├── tests/
│   │   │   ├── unit/                  # Unit tests
│   │   │   │   ├── components/
│   │   │   │   ├── hooks/
│   │   │   │   └── services/
│   │   │   ├── integration/           # Integration tests
│   │   │   ├── e2e/                   # End-to-end tests (Cypress/Playwright)
│   │   │   └── fixtures/              # Mock data
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── jest.config.js             # Unit test config
│   │   └── cypress.config.js           # E2E test config
│   │
│   └── driver-app/                    # Driver application (similar structure)
│       ├── src/
│       │   ├── pages/
│       │   │   ├── Dashboard.tsx       # Driver home
│       │   │   ├── RideDetail.tsx      # Current ride details
│       │   │   ├── Navigation.tsx      # Navigation to pickup/dropoff
│       │   │   ├── Earnings.tsx        # Earnings & payouts
│       │   │   └── Documents.tsx       # License, insurance verification
│       │   ├── components/
│       │   │   ├── RideAcceptCard/
│       │   │   ├── LiveNavigation/     # Turn-by-turn navigation
│       │   │   ├── PassengerWaiting/   # Countdown timer
│       │   │   └── EarningsChart/
│       │   ├── hooks/
│       │   │   ├── useNavigation.ts    # Navigation API wrapper
│       │   │   ├── useLocationTracking.ts  # Continuous location updates
│       │   │   └── usePalette.ts       # Drive time tracking
│       │   └── services/
│       │       ├── navigationService.ts  (Google Maps Directions API)
│       │       └── locationTracker.ts    (Battery-efficient GPS tracking)
│       └── ...
│
├── shared/                            # Shared code between apps
│   ├── components/
│   │   ├── LoadingSpinner.tsx
│   │   ├── ErrorBoundary.tsx
│   │   └── Toast.tsx
│   ├── hooks/
│   │   ├── useAsync.ts
│   │   └── useDebounce.ts
│   ├── utils/
│   │   ├── http.ts                    # Axios config
│   │   └── logger.ts
│   └── types/
│       └── index.ts                   # Shared type definitions
│
├── docs/
│   ├── SETUP.md                       # Frontend setup instructions
│   ├── TESTING.md                     # Testing guidelines
│   └── API_GUIDE.md                   # How to call backend APIs
│
├── package.json                       # Monorepo root (uses workspaces)
├── pnpm-workspace.yaml               # or lerna.json for monorepo management
├── .eslintrc.json
├── .prettierrc
└── README.md
```

---

## Backend Directory Structure

```
backend/
├── pom.xml                            # Root Maven config (multi-module)
│
├── shared/                            # Shared code library
│   ├── src/
│   │   ├── main/java/com/rideshare/
│   │   │   ├── model/
│   │   │   │   ├── Ride.java
│   │   │   │   ├── Driver.java
│   │   │   │   ├── User.java
│   │   │   │   ├── Location.java
│   │   │   │   └── PaymentMethod.java
│   │   │   ├── dto/                   # Data Transfer Objects
│   │   │   │   ├── RideRequestDTO.java
│   │   │   │   ├── RideResponseDTO.java
│   │   │   │   ├── DriverDTO.java
│   │   │   │   └── LocationDTO.java
│   │   │   ├── event/
│   │   │   │   ├── RideRequestedEvent.java
│   │   │   │   ├── RideMatchedEvent.java
│   │   │   │   ├── DriverLocationChangedEvent.java
│   │   │   │   └── RideCompletedEvent.java
│   │   │   ├── exception/
│   │   │   │   ├── RideNotFoundException.java
│   │   │   │   ├── DriverNotFoundException.java
│   │   │   │   ├── MatchingTimeoutException.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   └── constants/
│   │   │       └── AppConstants.java
│   │   └── test/java/...
│   └── pom.xml
│
├── auth-service/                      # Authentication microservice
│   ├── src/main/java/com/rideshare/auth/
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   ├── JwtTokenProvider.java
│   │   │   └── PasswordHasher.java
│   │   ├── security/
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── SecurityConfig.java
│   │   ├── repository/
│   │   │   └── RefreshTokenRepository.java
│   │   ├── config/
│   │   │   └── AuthConfig.java
│   │   └── AuthApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── rider-service/                     # Rider management service
│   ├── src/main/java/com/rideshare/rider/
│   │   ├── controller/
│   │   │   └── RiderController.java
│   │   ├── service/
│   │   │   ├── RiderService.java
│   │   │   └── PaymentService.java
│   │   ├── repository/
│   │   │   ├── RiderRepository.java
│   │   │   └── PaymentMethodRepository.java
│   │   ├── config/
│   │   │   └── RiderConfig.java
│   │   └── RiderApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── driver-service/                    # Driver management service
│   ├── src/main/java/com/rideshare/driver/
│   │   ├── controller/
│   │   │   └── DriverController.java
│   │   ├── service/
│   │   │   ├── DriverService.java
│   │   │   ├── AvailabilityService.java
│   │   │   └── EarningsCalculator.java
│   │   ├── repository/
│   │   │   ├── DriverRepository.java
│   │   │   └── VehicleRepository.java
│   │   ├── batch/
│   │   │   └── EarningsCalculationBatch.java  (scheduled task)
│   │   ├── config/
│   │   │   └── DriverConfig.java
│   │   └── DriverApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── location-service/                  # Real-time location management
│   ├── src/main/java/com/rideshare/location/
│   │   ├── controller/
│   │   │   └── LocationController.java
│   │   ├── service/
│   │   │   ├── LocationService.java
│   │   │   ├── LocationBatchProcessor.java  (async batching)
│   │   │   ├── GeoIndexService.java         (Redis Geo wrapper)
│   │   │   └── LocationEventPublisher.java
│   │   ├── model/
│   │   │   └── LocationUpdate.java
│   │   ├── config/
│   │   │   ├── RedisConfig.java
│   │   │   ├── KafkaProducerConfig.java
│   │   │   └── LocationConfig.java
│   │   └── LocationApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── ride-service/                      # Ride lifecycle management
│   ├── src/main/java/com/rideshare/ride/
│   │   ├── controller/
│   │   │   └── RideController.java
│   │   ├── service/
│   │   │   ├── RideService.java
│   │   │   ├── RideStateManager.java         (state machine)
│   │   │   ├── FareCalculator.java           (pricing logic)
│   │   │   ├── CancellationService.java
│   │   │   └── RideEventPublisher.java
│   │   ├── repository/
│   │   │   ├── RideRepository.java
│   │   │   └── RideShardRouter.java          (route to shard)
│   │   ├── config/
│   │   │   ├── PersistenceConfig.java
│   │   │   ├── KafkaProducerConfig.java
│   │   │   └── RideConfig.java
│   │   ├── saga/
│   │   │   └── RideSaga.java                 (distributed transaction handling)
│   │   └── RideApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── matching-service/                  # Ride matching engine (most critical)
│   ├── src/main/java/com/rideshare/matching/
│   │   ├── controller/
│   │   │   └── MatchingController.java       (internal API only)
│   │   ├── service/
│   │   │   ├── MatchingEngine.java           (main matching logic)
│   │   │   ├── MatchingQueue.java            (Kafka consumer)
│   │   │   ├── SpatialDiscovery.java         (Redis Geo queries)
│   │   │   ├── RankingAlgorithm.java         (scoring formula)
│   │   │   ├── DriverFetcher.java            (batch driver details)
│   │   │   ├── ETACalculator.java            (ETA integration)
│   │   │   ├── AssignmentService.java        (transactional assignment)
│   │   │   └── CircuitBreakerManager.java    (fault handling)
│   │   ├── cache/
│   │   │   ├── DriverDetailCache.java
│   │   │   ├── RouteCache.java               (route caching)
│   │   │   └── CacheInvalidationListener.java
│   │   ├── config/
│   │   │   ├── RedisConfig.java
│   │   │   ├── KafkaConsumerConfig.java
│   │   │   ├── ThreadPoolConfig.java         (thread pool sizing)
│   │   │   └── MatchingConfig.java
│   │   ├── metrics/
│   │   │   └── MatchingMetrics.java          (Prometheus metrics)
│   │   └── MatchingApplication.java
│   ├── src/test/java/
│   │   ├── unit/                             (unit tests)
│   │   ├── integration/                      (integration tests with Redis/Kafka)
│   │   └── performance/                      (load tests)
│   ├── pom.xml
│   └── Dockerfile
│
├── eta-service/                       # ETA calculation service
│   ├── src/main/java/com/rideshare/eta/
│   │   ├── controller/
│   │   │   └── ETAController.java
│   │   ├── service/
│   │   │   ├── ETAService.java
│   │   │   ├── RoutingAPIClient.java         (Google Maps/OSRM integration)
│   │   │   ├── TrafficPredictor.java         (ML-based traffic)
│   │   │   └── ETAEventPublisher.java
│   │   ├── cache/
│   │   │   └── RouteCache.java               (grid-based caching)
│   │   ├── config/
│   │   │   ├── RoutingAPIConfig.java
│   │   │   ├── CacheConfig.java
│   │   │   └── ETAConfig.java
│   │   └── ETAApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── notification-service/              # Real-time WebSocket notifications
│   ├── src/main/java/com/rideshare/notification/
│   │   ├── websocket/
│   │   │   ├── WebSocketHandler.java
│   │   │   ├── WebSocketController.java
│   │   │   ├── ConnectionManager.java        (track active connections)
│   │   │   ├── MessageRouter.java            (route messages to users)
│   │   │   └── HeartbeatManager.java         (ping/pong)
│   │   ├── event/
│   │   │   ├── EventProcessor.java
│   │   │   ├── EventDeduplicator.java        (idempotency)
│   │   │   └── BacklogService.java           (offline message delivery)
│   │   ├── service/
│   │   │   ├── NotificationService.java
│   │   │   └── PushNotificationService.java  (mobile push)
│   │   ├── redis/
│   │   │   └── RedisPubSubAdapter.java       (Pub/Sub integration)
│   │   ├── config/
│   │   │   ├── WebSocketConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   ├── KafkaConsumerConfig.java
│   │   │   └── NotificationConfig.java
│   │   ├── metrics/
│   │   │   └── WebSocketMetrics.java
│   │   └── NotificationApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── payment-service/                   # Payment processing (external integration)
│   ├── src/main/java/com/rideshare/payment/
│   │   ├── controller/
│   │   │   └── PaymentController.java
│   │   ├── service/
│   │   │   ├── PaymentService.java
│   │   │   ├── StripeAdapter.java             (Stripe integration)
│   │   │   ├── RefundService.java
│   │   │   └── PaymentEventPublisher.java
│   │   ├── config/
│   │   │   ├── PaymentConfig.java
│   │   │   └── StripeConfig.java
│   │   └── PaymentApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
│
├── gateway/                           # API Gateway
│   ├── src/main/java/com/rideshare/gateway/
│   │   ├── filter/
│   │   │   ├── AuthenticationFilter.java
│   │   │   ├── RateLimitFilter.java
│   │   │   ├── RequestLoggingFilter.java
│   │   │   └── MetricsFilter.java
│   │   ├── config/
│   │   │   ├── RouteConfig.java              (service routing rules)
│   │   │   ├── SecurityConfig.java
│   │   │   └── GatewayConfig.java
│   │   └── GatewayApplication.java
│   ├── src/test/java/...
│   ├── pom.xml
│   ├── application.yml                 (routes, timeout config)
│   └── Dockerfile
│
├── docs/
│   ├── SETUP.md                       # Backend setup instructions
│   ├── TESTING.md                     # Testing guidelines
│   ├── DEPLOYMENT.md                  # Deployment procedures
│   └── API_DOCUMENTATION.md           # API docs (Swagger)
│
└── pom.xml                            # Root POM with all modules
```

---

## Infrastructure Directory Structure

```
infrastructure/
├── kubernetes/
│   ├── namespaces.yaml
│   ├── config/
│   │   ├── configmaps/
│   │   │   ├── app-config.yaml        # App configuration
│   │   │   └── logging-config.yaml
│   │   └── secrets/
│   │       ├── database-creds.yaml    (sealed secrets)
│   │       ├── api-keys.yaml
│   │       └── jwt-secrets.yaml
│   │
│   ├── deployments/
│   │   ├── auth-service.yaml
│   │   ├── rider-service.yaml
│   │   ├── driver-service.yaml
│   │   ├── location-service.yaml
│   │   ├── ride-service.yaml
│   │   ├── matching-service.yaml      (critical component)
│   │   ├── eta-service.yaml
│   │   ├── notification-service.yaml
│   │   ├── payment-service.yaml
│   │   └── gateway.yaml
│   │
│   ├── services/
│   │   ├── auth-service-svc.yaml
│   │   ├── ... (one per microservice)
│   │   └── gateway-svc.yaml           (LoadBalancer, external)
│   │
│   ├── statefulsets/
│   │   ├── postgres-primary.yaml
│   │   ├── postgres-replica.yaml
│   │   ├── redis-cluster.yaml
│   │   ├── kafka-broker.yaml
│   │   └── elasticsearch.yaml         (logging)
│   │
│   ├── ingress/
│   │   └── main-ingress.yaml          (routing, TLS)
│   │
│   ├── hpa/                           # Horizontal Pod Autoscaling
│   │   ├── matching-service-hpa.yaml  (scale by CPU/custom metrics)
│   │   ├── location-service-hpa.yaml
│   │   └── rider-service-hpa.yaml
│   │
│   ├── pdb/                           # Pod Disruption Budgets
│   │   ├── matching-service-pdb.yaml  (min 2 replicas available)
│   │   └── notification-service-pdb.yaml
│   │
│   ├── monitoring/
│   │   ├── prometheus-config.yaml
│   │   ├── grafana-deployment.yaml
│   │   ├── prometheus-rules.yaml      (alerting rules)
│   │   └── servicemonitor.yaml        (Prometheus Operator)
│   │
│   ├── logging/
│   │   ├── elasticsearch-deployment.yaml
│   │   ├── kibana-deployment.yaml
│   │   └── fluentd-daemonset.yaml     (log collection)
│   │
│   ├── networking/
│   │   ├── network-policy.yaml        (service-to-service traffic control)
│   │   └── pod-security-policy.yaml
│   │
│   └── kustomization.yaml             # Kustomize overlays for dev/staging/prod
│       └── overlays/
│           ├── dev/
│           ├── staging/
│           └── prod/
│
├── docker/
│   ├── Dockerfile.base                # Base Java image (multi-stage)
│   ├── Dockerfile.gradle              # Build with gradle
│   └── docker-compose.yaml            # Local development
│
├── helm/                              # Helm charts for deployment
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── templates/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── configmap.yaml
│   │   └── secret.yaml
│   └── values-prod.yaml               # Production overrides
│
├── terraform/                         # IaC for cloud infrastructure (optional)
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── kubernetes/                    # K8s cluster provisioning
│   ├── rds/                           # Database provisioning
│   ├── elasticache/                   # Redis provisioning
│   └── vpc/                           # Network provisioning
│
├── scripts/
│   ├── setup-cluster.sh               # Initialize K8s cluster
│   ├── deploy.sh                      # Deploy to K8s
│   ├── rollback.sh                    # Rollback deployment
│   ├── migrate-db.sh                  # Database migration
│   ├── backup-db.sh                   # Database backup
│   └── healthcheck.sh                 # Health check script
│
├── monitoring/
│   ├── prometheus/
│   │   ├── prometheus.yml             # Prometheus config
│   │   └── alerts.yml                 # Alert rules
│   ├── grafana/
│   │   └── dashboards/
│   │       ├── matching-engine.json   # Matching latency/throughput
│   │       ├── location-service.json  # Location update latency
│   │       ├── ride-service.json      # Ride lifecycle metrics
│   │       ├── system-health.json     # CPU, memory, disk
│   │       └── slo-dashboard.json     # SLO compliance
│   └── alertmanager/
│       └── alertmanager.yml
│
├── logging/
│   ├── filebeat.yml                   # Log shipping config
│   ├── logstash-config.conf           # Log transformation
│   └── kibana-dashboards/
│       ├── ride-matching.json
│       └── error-tracking.json
│
└── README.md                          # Infrastructure setup guide
```

---

## Build & CI/CD Configuration

```
.github/workflows/                     # GitHub Actions workflows
├── backend-ci.yml                    # Build, test backend
├── frontend-ci.yml                   # Build, test frontend
├── integration-tests.yml              # Integration test suite
├── deploy-staging.yml                 # Deploy to staging
└── deploy-prod.yml                    # Deploy to production

.gitlab-ci.yml                         # Alternative: GitLab CI config

Jenkinsfile                            # Alternative: Jenkins pipeline

.dockerignore
.gitignore
```

---

## Documentation Structure

```
docs/
├── ARCHITECTURE.md                    # System design overview
├── API_GUIDE.md                       # API documentation
├── DATABASE_SCHEMA.md                 # DB design guide
├── DEPLOYMENT_GUIDE.md                # How to deploy
├── TROUBLESHOOTING.md                 # Common issues & fixes
├── PERFORMANCE_TUNING.md              # Optimization guide
├── SECURITY_GUIDE.md                  # Security best practices
├── ADR/                               # Architecture Decision Records
│   ├── 0001-use-postgres-for-rides.md
│   ├── 0002-redis-for-locations.md
│   ├── 0003-kafka-for-events.md
│   └── 0004-websocket-for-realtime.md
├── diagrams/
│   ├── architecture.png
│   ├── data-flow.png
│   ├── matching-algorithm.png
│   └── deployment.png
└── runbooks/
    ├── incident-response.md
    ├── scaling-guide.md
    └── backup-recovery.md
```

---

## Configuration Files (Root Level)

```
.env.example                           # Example environment variables
docker-compose.yml                     # Local dev environment
Makefile                               # Convenient commands (make test, make deploy)
README.md                              # Project overview
CONTRIBUTING.md                        # Contribution guidelines
LICENSE                                # License file
```

---

## Monorepo Workspace Management

### Option A: pnpm workspaces (Recommended for frontend)

```
frontend/
├── pnpm-workspace.yaml
└── package.json
    {
      "workspaces": ["apps/*", "shared"]
    }
```

### Option B: Lerna (Alternative)

```
frontend/
├── lerna.json
└── package.json
```

### Option C: Maven modules (Backend)

```
backend/
└── pom.xml
    <modules>
      <module>shared</module>
      <module>auth-service</module>
      ...
    </modules>
```

---

## Development Workflow Example

```
# Frontend development
cd frontend/apps/rider-app
pnpm install
pnpm dev              # Dev server at localhost:3000

# Backend development
cd backend
mvn clean install    # Build all modules
mvn spring-boot:run -pl matching-service  # Run specific service

# Infrastructure development
cd infrastructure
kubectl apply -f kubernetes/dev/  # Deploy to local K8s
kubectl port-forward svc/gateway 3000:80  # Access locally

# Running tests
cd frontend
pnpm test            # Unit tests (Jest)
pnpm test:e2e        # End-to-end tests (Cypress)

cd backend
mvn test             # Unit tests
mvn failsafe:integration-test  # Integration tests
```

---

## Summary: Folder Structure Decisions

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **Monorepo (frontend)** | Shared code; consistent tooling | Build complexity; version management |
| **Multi-module Maven (backend)** | Shared libraries; clear boundaries | Build time; dependency management |
| **Docker per service** | Independent deployment; isolation | Image management; registry size |
| **Kubernetes native** | Industry standard; auto-scaling; self-healing | Operational complexity; learning curve |
| **Terraform IaC** | Reproducible infrastructure; version control | Steep learning curve; AWS-specific |
| **Helm charts** | Package management; templating; overrides | Extra abstraction layer |

---

**Next**: Non-Functional Requirements
