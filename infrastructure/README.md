# Ride-Sharing Platform - Infrastructure & DevOps

Complete containerization, orchestration, and observability infrastructure for the ride-sharing platform microservices architecture.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Quick Start - Local Development](#quick-start-local-development)
4. [Docker & Containerization](#docker--containerization)
5. [Kubernetes Deployment](#kubernetes-deployment)
6. [CI/CD Pipelines](#cicd-pipelines)
7. [Monitoring & Observability](#monitoring--observability)
8. [Database & Persistence](#database--persistence)
9. [Troubleshooting](#troubleshooting)

## Overview

This infrastructure manages:

- **8 Microservices**: Auth, Rider, Driver, Ride, Location, Matching Engine, ETA, Notification
- **3 Frontend Applications**: Rider App, Driver App, Admin Dashboard
- **Stateful Services**: PostgreSQL (replication), Redis (cluster), Kafka (3 brokers)
- **Monitoring Stack**: Prometheus, Grafana, ELK Stack, Jaeger
- **CI/CD**: GitHub Actions for automated testing, building, and deployment

### Key Features

✅ Multi-stage Docker builds for optimized images
✅ Kubernetes StatefulSets for database and message queue
✅ Horizontal Pod Autoscaling based on CPU/Memory
✅ Pod Disruption Budgets for high availability
✅ Health checks (liveness & readiness probes)
✅ Resource quotas and limits
✅ Security contexts (non-root users, read-only filesystems)
✅ Comprehensive monitoring and alerting
✅ Automated CI/CD with rollback capability

## Architecture

```
┌─────────────────────────────────────────────────────┐
│         GitHub Actions (CI/CD Pipelines)           │
│  Build → Test → Docker Image → Push → Deploy       │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│         Kubernetes Cluster (3+ Availability Zones) │
│                                                     │
│  ┌───────────────────────────────────────────┐    │
│  │ Microservices Namespace (8 services)      │    │
│  │ - 2-7 replicas each (scaled by HPA)       │    │
│  │ - API Gateway (nginx)                     │    │
│  │ - Ingress Controller                      │    │
│  └───────────────────────────────────────────┘    │
│                                                     │
│  ┌───────────────────────────────────────────┐    │
│  │ Data Layer (PostgreSQL, Redis, Kafka)    │    │
│  │ - PostgreSQL: 1 primary + 3 replicas     │    │
│  │ - Redis: 6 nodes (3 primary + 3 replica) │    │
│  │ - Kafka: 3 brokers (7-day retention)     │    │
│  └───────────────────────────────────────────┘    │
│                                                     │
│  ┌───────────────────────────────────────────┐    │
│  │ Monitoring (Prometheus, Grafana, ELK)    │    │
│  │ - Metrics collection (15s intervals)     │    │
│  │ - Custom dashboards & alerting          │    │
│  │ - Centralized logging                   │    │
│  └───────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

## Quick Start - Local Development

### Prerequisites

- Docker & Docker Compose (v3.9+)
- kubectl (v1.27+) for Kubernetes
- Helm 3 (for production deployments)
- Git

### Start Local Environment

```bash
cd infrastructure/docker

# Start all services (PostgreSQL, Redis, Kafka, microservices)
docker-compose up -d

# Verify services are running
docker-compose ps

# View logs for a specific service
docker-compose logs -f rider-service

# Access web interfaces
# API Gateway:        http://localhost:8000
# Rider App:          http://localhost:3001
# Driver App:         http://localhost:3002
# Admin Dashboard:    http://localhost:3003
# Grafana:            http://localhost:3000 (admin/admin)
# Prometheus:         http://localhost:9090
```

### Test Endpoints

```bash
# Health check - Auth Service
curl http://localhost:8001/actuator/health

# Rider Service
curl http://localhost:8002/actuator/health

# Via API Gateway
curl http://localhost:8000/api/auth/health
curl http://localhost:8000/api/riders/health
```

### Cleanup

```bash
# Stop all services
docker-compose down

# Remove volumes (data will be deleted)
docker-compose down -v

# Remove images
docker-compose down --rmi all
```

## Docker & Containerization

### Dockerfile Structure

All Dockerfiles use multi-stage builds:

1. **Builder Stage**: Compile and package application
2. **Runtime Stage**: Minimal JRE/Node.js base image

### Backend Services (Java 21)

Location: `infrastructure/docker/backend/Dockerfile.*`

Example build:

```bash
docker build -f infrastructure/docker/backend/Dockerfile.auth -t rideshare/auth-service:latest .
```

Key optimizations:
- JVM memory limits scaled per service (256MB-2GB)
- G1GC garbage collection with pause time targets
- Non-root user execution
- Health checks configured
- Read-only root filesystem

### Frontend Applications (Node.js + Vite)

Location: `infrastructure/docker/frontend/Dockerfile.*`

Multi-stage process:
1. Dependencies installation
2. Vite build (production bundles)
3. Nginx serving static assets

```bash
docker build -f infrastructure/docker/frontend/Dockerfile.rider-app -t rideshare/rider-app:latest .
```

### API Gateway

Location: `infrastructure/docker/api-gateway.conf`

Nginx-based API Gateway with:
- Service routing (8 backend routes)
- WebSocket support (notification service)
- Rate limiting (100 req/s global, 1 req/s per client for WebSocket)
- Health checks
- Request logging

### Image Registry

Images are pushed to:

```
docker.io/rideshare/<service-name>:<tag>
```

Tags:
- `main-<commit-sha>` - Main branch builds
- `develop-<commit-sha>` - Develop branch builds
- `latest` - Latest from main branch
- `v1.0.0` - Release tags

## Kubernetes Deployment

### Prerequisites

1. **Kubernetes Cluster** (1.27+)
   - AWS EKS, Google GKE, or self-managed

2. **Storage Classes**
   - `ebs-gp3`: General purpose (microservices)
   - `ebs-ssd`: High-performance (Prometheus/metrics)
   - See `infrastructure/kubernetes/storage/storage-class.yaml`

3. **Load Balancer**
   - External load balancer for Ingress
   - CloudFlare, AWS ALB, or NGINX

### Namespace Structure

```
microservices/    - 8 microservices, 3 frontend apps
data-layer/       - PostgreSQL, Redis, Kafka
monitoring/       - Prometheus, Grafana, ELK Stack
ingress-nginx/    - Ingress controller
```

### Deploy to Kubernetes

```bash
# 1. Create namespaces
kubectl apply -f infrastructure/kubernetes/namespaces/namespaces.yaml

# 2. Create secrets (customize values!)
kubectl apply -f infrastructure/kubernetes/secrets.yaml

# 3. Apply storage classes
kubectl apply -f infrastructure/kubernetes/storage/storage-class.yaml

# 4. Deploy data layer (PostgreSQL, Redis, Kafka)
kubectl apply -f infrastructure/kubernetes/data/postgresql-statefulset.yaml
kubectl apply -f infrastructure/kubernetes/data/redis-cluster.yaml
kubectl apply -f infrastructure/kubernetes/data/kafka-cluster.yaml

# Wait for data layer
kubectl wait --for=condition=ready pod -l app=postgresql -n data-layer --timeout=300s

# 5. Deploy microservices
kubectl apply -f infrastructure/kubernetes/services/

# 6. Deploy monitoring
kubectl apply -f infrastructure/monitoring/prometheus/

# Verify deployment
kubectl get deployments -n microservices
kubectl get statefulsets -n data-layer
kubectl get services -n microservices
```

### Verify Deployment

```bash
# Check pod status
kubectl get pods -n microservices
kubectl get pods -n data-layer

# View pod logs
kubectl logs -f deployment/matching-engine -n microservices

# Port-forward to access services
kubectl port-forward svc/auth-service 8080:8080 -n microservices

# Check service endpoints
kubectl get endpoints -n microservices
```

### Scaling

**Manual scaling:**
```bash
kubectl scale deployment rider-service --replicas=6 -n microservices
```

**View HPA status:**
```bash
kubectl get hpa -n microservices
kubectl describe hpa rider-service -n microservices
```

### Rollback Deployment

```bash
# View rollout history
kubectl rollout history deployment/matching-engine -n microservices

# Rollback to previous version
kubectl rollout undo deployment/matching-engine -n microservices

# Rollback to specific revision
kubectl rollout undo deployment/matching-engine --to-revision=2 -n microservices
```

## CI/CD Pipelines

### GitHub Actions Workflows

Location: `.github/workflows/`

#### 1. CI Build & Test (`ci.yaml`)

**Triggers**: Push to main/develop, Pull Requests

**Jobs**:
- `build-backend`: Maven build + unit tests for all 8 services
- `build-frontend`: npm install + build + tests for 3 apps
- `code-quality`: Trivy security scan, Dependabot checks
- `integration-tests`: Tests with real databases

```bash
# View workflow runs
gh run list --workflow=ci.yaml

# Re-run a workflow
gh run rerun <run-id>
```

#### 2. Build & Push Docker Images (`build-docker.yaml`)

**Triggers**: Push to main/develop with code changes

**Jobs**:
- Build backend images (8 services)
- Build frontend images (3 apps)
- Scan images for vulnerabilities (Trivy)
- Notify Slack on completion

Images pushed to: `docker.io/rideshare/<service-name>`

#### 3. Deploy to Dev (`deploy-dev.yaml`)

**Triggers**: Push to develop branch

**Jobs**:
- Update kubeconfig (AWS EKS)
- Create namespaces and secrets
- Deploy data layer (PostgreSQL, Redis, Kafka)
- Deploy microservices
- Deploy monitoring
- Smoke tests
- Slack notification

#### 4. Deploy to Staging (`deploy-staging.yaml`)

**Triggers**: Manual trigger from GitHub UI

**Jobs**:
- Same as dev deployment
- Manual approval required
- Runs performance tests

#### 5. Deploy to Production (`deploy-prod.yaml`)

**Triggers**: Manual trigger with approval

**Jobs**:
- Blue-green deployment strategy
- Route 10% traffic initially
- Gradually increase to 100%
- Automated rollback on errors
- PagerDuty notification

### Secrets Management

GitHub Actions secrets:

```
DOCKER_USERNAME           - Docker Hub username
DOCKER_PASSWORD           - Docker Hub password
AWS_ACCESS_KEY_ID         - AWS credentials for EKS
AWS_SECRET_ACCESS_KEY     - AWS secret
POSTGRES_DEV_PASSWORD     - Dev PostgreSQL password
JWT_SECRET                - JWT signing secret
ROUTING_API_KEY           - Google Maps API key
PAYMENT_PROCESSOR_KEY     - Payment processor key
NOTIFICATION_SERVICE_KEY  - Notification service API key
SLACK_WEBHOOK_URL         - Slack webhook for notifications
SONAR_HOST_URL            - SonarQube server URL
SONAR_TOKEN               - SonarQube authentication token
```

### Code Coverage Requirements

- Minimum 80% code coverage
- Enforced in CI pipeline
- Failed coverage blocks merge

## Monitoring & Observability

### Prometheus

**Configuration**: `infrastructure/monitoring/prometheus/`

- **Scrape Interval**: 15 seconds
- **Retention**: 30 days
- **Targets**:
  - Kubernetes API server & nodes
  - Microservices (via annotations)
  - PostgreSQL, Redis, Kafka
  - Nginx ingress controller

**Access**: http://prometheus:9090 (or port-forward)

### Grafana

**Dashboards**: `infrastructure/monitoring/grafana/dashboards/`

Pre-built dashboards:
1. **System Overview** - CPU, memory, disk, network
2. **Service Metrics** - Request rate, latency, error rate
3. **Database Metrics** - Connections, query latency, replication lag
4. **Matching Engine** - Latency distribution, match success rate
5. **Location Service** - Throughput, queue depth, cache hit rate

**Access**: http://grafana:3000
**Default Credentials**: admin / admin

### Alerting

**Rules**: `infrastructure/monitoring/prometheus/rules.yaml`

Key alerts:
- Matching engine latency > 200ms (P95)
- Error rate > 1%
- Location service throughput < 80k/sec
- PostgreSQL replication lag > 30s
- Redis memory > 90%
- Pod crash loops
- Node disk/memory pressure

**AlertManager**: Sends notifications to:
- Slack channels
- PagerDuty
- Email

### Jaeger Distributed Tracing

Configuration: `infrastructure/monitoring/jaeger/`

**Tracing**: All microservices
**Sampling**: 10% of requests (configurable)
**Retention**: 72 hours
**Backend**: Elasticsearch

**Access**: http://jaeger:6831 (UDP) / http://jaeger:16686 (Web UI)

### ELK Stack

- **Elasticsearch**: Centralized log storage
- **Logstash**: Log parsing and processing
- **Kibana**: Log search and visualization

**Log retention**: 30 days
**Index rotation**: Daily

**Access**: http://kibana:5601

## Database & Persistence

### PostgreSQL

**Configuration**: `kubernetes/data/postgresql-statefulset.yaml`

- **Replicas**: 1 primary + 3 standbys (HA)
- **Storage**: 100Gi per instance (EBS gp3)
- **Replication**: Streaming replication
- **Failover**: Manual (can be automated with pg_auto_failover)

**Backup Strategy**:
- WAL archival to S3 (continuous)
- Daily snapshots
- RTO: 15 minutes
- RPO: 5 minutes

**Access**:
```bash
# Port-forward to primary
kubectl port-forward svc/postgresql-primary 5432:5432 -n data-layer

# Connect
psql -h localhost -U postgres -d rideshare
```

### Redis Cluster

**Configuration**: `kubernetes/data/redis-cluster.yaml`

- **Nodes**: 6 (3 primary + 3 replica)
- **Storage**: 50Gi per node (EBS gp3)
- **Replication Factor**: 1
- **Eviction Policy**: allkeys-lru

**High Availability**:
- Automatic failover if primary goes down
- Min 1 replica available for write operations

### Kafka Cluster

**Configuration**: `kubernetes/data/kafka-cluster.yaml`

- **Brokers**: 3
- **Storage**: 200Gi per broker (EBS gp3)
- **Replication Factor**: 3
- **Retention**: 7 days
- **Compression**: Snappy

**Topics** (auto-created):
- `rider-events` - Rider lifecycle events
- `driver-events` - Driver events
- `ride-events` - Ride updates
- `location-updates` - Real-time location stream
- `notifications` - Outgoing notifications

### Disaster Recovery

**PostgreSQL Recovery**:
```bash
# List backups
aws s3 ls s3://rideshare-backups/postgres/

# Restore from backup
pg_basebackup -R -D /var/lib/postgresql/data -h <primary-host> -U postgres

# Replay WAL
# PostgreSQL automatically replays after restart
```

## Troubleshooting

### Common Issues

#### 1. Pod in CrashLoopBackOff

```bash
# Check pod logs
kubectl logs <pod-name> -n microservices --previous

# Check pod events
kubectl describe pod <pod-name> -n microservices

# Check resource availability
kubectl top nodes
kubectl describe node <node-name>
```

#### 2. Pending Pods

```bash
# Usually caused by PVC not binding
kubectl get pvc -n data-layer
kubectl describe pvc <pvc-name> -n data-layer

# Check storage class
kubectl get storageclass

# Ensure StorageClass provisioner is running
kubectl get pods -n kube-system | grep ebs
```

#### 3. Service Connectivity Issues

```bash
# Test DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -- \
  nslookup auth-service.microservices.svc.cluster.local

# Test service endpoint
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://auth-service.microservices.svc.cluster.local:8080/actuator/health
```

#### 4. Database Connection Errors

```bash
# Check PostgreSQL pod status
kubectl get pods -n data-layer | grep postgresql

# Check credentials
kubectl get secret postgres-credentials -n data-layer -o yaml

# Test database connectivity
kubectl run psql --image=postgres:15 --rm -it -- \
  psql -h postgresql-primary.data-layer.svc.cluster.local -U postgres -d rideshare -c "SELECT 1"
```

#### 5. High Memory Usage

```bash
# Check pod memory usage
kubectl top pods -n microservices

# Increase memory limit
kubectl set resources deployment/<service> \
  --limits=memory=2Gi -n microservices

# Check JVM heap settings
kubectl logs <pod-name> -n microservices | grep Xmx
```

### Performance Tuning

#### Matching Engine Optimization

The matching engine is the most compute-intensive service:

1. **Increase Replicas** (if CPU > 70%)
   ```bash
   kubectl scale deployment matching-engine --replicas=10 -n microservices
   ```

2. **Increase Resource Limits**
   ```yaml
   resources:
     requests:
       cpu: "4000m"
       memory: "3072Mi"
     limits:
       cpu: "8000m"
       memory: "6144Mi"
   ```

3. **Tune JVM Settings**
   ```
   -XX:MaxGCPauseMillis=100
   -XX:ParallelGCThreads=8
   -XX:ConcGCThreads=2
   ```

#### Location Service Throughput

Location service handles 100k+ location updates/sec:

1. **Increase replicas to 10+**
2. **Use Redis Cluster** for distributed caching
3. **Enable batching** in microservice (batch size 1000)
4. **Tune Kafka** producer settings (batch.size, linger.ms)

### Monitoring Performance

```bash
# Check HPA metrics
kubectl get hpa -n microservices -w

# View actual CPU usage
kubectl top pods -n microservices --sort-by=memory

# Check metrics from Prometheus
# Access Grafana dashboards for visual analysis
```

## Documentation & References

- **Kubernetes Docs**: https://kubernetes.io/docs/
- **Docker Docs**: https://docs.docker.com/
- **Prometheus**: https://prometheus.io/docs/
- **Grafana**: https://grafana.com/docs/
- **AWS EKS**: https://docs.aws.amazon.com/eks/

## Support & Escalation

1. **Issue in microservice**:
   - Check pod logs: `kubectl logs <pod-name> -n microservices`
   - Check metrics: Grafana dashboards
   - Check alerts: Prometheus AlertManager

2. **Database issue**:
   - Check PostgreSQL pod status
   - Verify replication lag
   - Check disk space on EBS volumes

3. **Deployment failure**:
   - Review GitHub Actions workflow logs
   - Check image vulnerabilities (Trivy scan)
   - Verify Kubernetes cluster health

4. **Performance degradation**:
   - Check HPA metrics
   - Review Grafana dashboards
   - Analyze Jaeger traces

---

**Last Updated**: 2026-06-02
**Managed By**: DevOps Team
**Contact**: devops@rideshare.internal
