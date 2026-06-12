# Location Service - Deployment Guide

## Build

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker (for containerization)

### Local Build
```bash
cd backend/location-service
mvn clean package -DskipTests
```

### With Tests (recommended)
```bash
mvn clean package
# Runs 80+ unit tests
# ~3 minutes total
```

### Docker Build
```bash
mvn clean package
docker build -t rideshare/location-service:1.0.0 -f Dockerfile .
docker run -it -p 8082:8082 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  -e KAFKA_BROKERS=kafka:9092 \
  rideshare/location-service:1.0.0
```

## Local Development

### Docker Compose (Full Stack)
```bash
cd infrastructure/docker
docker-compose up -d

# Services:
# - PostgreSQL: localhost:5432 (postgres/postgres)
# - Redis: localhost:6379
# - Kafka: localhost:9092
# - Zookeeper: localhost:2181
```

### Run Location Service Locally
```bash
# Terminal 1: Start dependencies
docker-compose up

# Terminal 2: Run application
cd backend/location-service
mvn spring-boot:run

# Service runs at localhost:8082
```

### Test Endpoints
```bash
# 1. Update location
curl -X PUT http://localhost:8082/api/v1/locations/drivers/location \
  -H "Content-Type: application/json" \
  -d '{
    "driverId": "driver-123",
    "lat": 40.7128,
    "lng": -74.0060,
    "heading": 180,
    "speed": 15.5,
    "accuracy": 10.0,
    "timestamp": "2026-06-02T12:34:56Z",
    "source": "gps"
  }'

# Response: 202 Accepted

# 2. Find nearby drivers
curl http://localhost:8082/api/v1/locations/nearby?lat=40.7128&lng=-74.0060&radiusKm=5

# Response: 200 OK with drivers list

# 3. Get current location
curl http://localhost:8082/api/v1/locations/drivers/driver-123/location

# 4. Get batch stats
curl http://localhost:8082/api/v1/locations/stats
```

## Kubernetes Deployment

### Prerequisites
- Kubernetes cluster (1.24+)
- kubectl configured
- Helm 3+ (optional, for chart deployment)

### 1. Create Namespace
```bash
kubectl create namespace location-service
```

### 2. Create Secrets
```bash
kubectl create secret generic location-service-secrets \
  --from-literal=db-password=postgres \
  --from-literal=redis-password=redis \
  -n location-service
```

### 3. Create ConfigMap
```bash
kubectl create configmap location-service-config \
  --from-literal=DB_HOST=postgres.default \
  --from-literal=DB_PORT=5432 \
  --from-literal=REDIS_HOST=redis.default \
  --from-literal=KAFKA_BROKERS=kafka.default:9092 \
  -n location-service
```

### 4. Deploy Using Kubectl
```bash
# Create deployment
kubectl apply -f infrastructure/kubernetes/deployments/location-service.yaml -n location-service

# Verify deployment
kubectl rollout status deployment/location-service -n location-service

# Check pods
kubectl get pods -n location-service
```

### 5. Expose Service
```bash
# Create service
kubectl apply -f infrastructure/kubernetes/services/location-service.yaml

# Port forward for local testing
kubectl port-forward -n location-service svc/location-service 8082:8082
```

### 6. Check Logs
```bash
# Follow logs from main pod
kubectl logs -f deployment/location-service -n location-service --tail=100

# Logs from specific pod
kubectl logs pod/location-service-xyz -n location-service
```

### 7. Scale Deployment
```bash
# Scale to 3 replicas
kubectl scale deployment location-service --replicas=3 -n location-service

# Verify
kubectl get deployment location-service -n location-service
```

## Helm Deployment (Recommended)

### Create Helm Chart
```bash
helm create location-service

# Update values.yaml
helm install location-service ./location-service -n location-service

# Upgrade
helm upgrade location-service ./location-service -n location-service

# Rollback
helm rollback location-service 1 -n location-service
```

### values.yaml Example
```yaml
replicaCount: 3

image:
  repository: rideshare/location-service
  tag: "1.0.0"

service:
  type: ClusterIP
  port: 8082

ingress:
  enabled: true
  hosts:
    - host: location-service.internal
      paths:
        - path: /
          pathType: Prefix

resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

env:
  DB_HOST: postgres.default
  DB_PORT: "5432"
  REDIS_HOST: redis.default
  KAFKA_BROKERS: "kafka.default:9092"
```

## Configuration Files

### kubernetes/deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: location-service
  labels:
    app: location-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: location-service
  template:
    metadata:
      labels:
        app: location-service
    spec:
      containers:
      - name: location-service
        image: rideshare/location-service:1.0.0
        ports:
        - containerPort: 8082
        env:
        - name: DB_HOST
          valueFrom:
            configMapKeyRef:
              name: location-service-config
              key: DB_HOST
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: location-service-secrets
              key: db-password
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/ready
            port: 8082
          initialDelaySeconds: 20
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
```

## Health Checks

### Liveness Probe (Is service alive?)
```bash
curl http://location-service:8082/actuator/health

# Response:
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "kafka": {"status": "UP"}
  }
}
```

### Readiness Probe (Is service ready for traffic?)
```bash
curl http://location-service:8082/actuator/health/ready

# Response: 200 OK if ready, 503 if not
```

## Monitoring

### Prometheus Scraping
```yaml
# prometheus.yaml
scrape_configs:
  - job_name: 'location-service'
    static_configs:
      - targets: ['localhost:8082']
    metrics_path: '/actuator/prometheus'
```

### Key Metrics to Monitor
```
location_updates_total             # Should increase ~100k/sec
location_batch_flushes_total       # Should be ~1000-2000/sec
location_nearby_query_latency_p99  # Should be < 200ms
location_redis_batch_latency_p99   # Should be < 10ms
```

### Grafana Dashboards
```json
{
  "dashboard": {
    "title": "Location Service Overview",
    "panels": [
      {
        "title": "Updates Per Second",
        "targets": [{"expr": "rate(location_updates_total[1m])"}]
      },
      {
        "title": "Nearby Query Latency (p99)",
        "targets": [{"expr": "location_nearby_query_latency_p99"}]
      },
      {
        "title": "Batch Flush Rate",
        "targets": [{"expr": "rate(location_batch_flushes_total[1m])"}]
      }
    ]
  }
}
```

## Troubleshooting

### Issue: High CPU Usage
```
Root Cause: Too many batch flushes (high throughput)
Solution: Increase batch size (500 → 1000) or batch window (100ms → 200ms)
```

### Issue: OOM (Out of Memory)
```
Root Cause: Input queue filling up, not flushing
Solution: Check Redis connectivity, increase Redis batch write parallelism
Monitoring: Watch location_stats endpoint for queue size
```

### Issue: Slow Nearby Queries
```
Root Cause: Large number of drivers in Redis Geo (> 100k per node)
Solution: Implement geographic sharding (by region/country)
OR: Upgrade Redis (add replicas for read scaling)
```

### Issue: Missing Location Updates in PostgreSQL
```
Root Cause: DB writer thread crashed
Solution: Restart pod, manually replay from Kafka topic
Command: kafka-console-consumer --topic location.changed --from-beginning
```

### Issue: Kafka Producer Timeout
```
Root Cause: Kafka broker slow/unavailable
Solution: Check Kafka broker health, verify network connectivity
Note: Location data still indexed in Redis, events can be replayed
```

## Rolling Deployment

### Blue-Green Deployment
```bash
# 1. Deploy v2 (green) alongside v1 (blue)
kubectl set image deployment/location-service-green \
  location-service=rideshare/location-service:2.0.0

# 2. Wait for green to be ready
kubectl rollout status deployment/location-service-green

# 3. Switch traffic to green
kubectl patch service location-service \
  -p '{"spec":{"selector":{"version":"v2"}}}'

# 4. Monitor for issues

# 5. If rollback needed:
kubectl patch service location-service \
  -p '{"spec":{"selector":{"version":"v1"}}}'
```

### Canary Deployment
```bash
# 1. Deploy v2 with 10% traffic
kubectl set image deployment/location-service \
  location-service=rideshare/location-service:2.0.0 --record

# 2. Use traffic splitting (via Istio):
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: location-service
spec:
  hosts:
  - location-service
  http:
  - match:
    - uri:
        prefix: /
    route:
    - destination:
        host: location-service
        subset: v1
      weight: 90
    - destination:
        host: location-service
        subset: v2
      weight: 10
EOF

# 3. Gradually increase traffic to v2
# 4. Full rollout when confident
```

## Backup & Recovery

### PostgreSQL Backup
```bash
# Backup location history
kubectl exec -it postgres-pod -n location-service -- \
  pg_dump -U postgres location_service > backup.sql

# Restore
kubectl exec -it postgres-pod -n location-service -- \
  psql -U postgres location_service < backup.sql
```

### Redis Backup
```bash
# Save snapshot
kubectl exec -it redis-pod -n location-service -- \
  redis-cli BGSAVE

# Copy RDB file
kubectl cp location-service/redis-pod:/data/dump.rdb ./redis-backup.rdb
```

### Kafka Topic Backup
```bash
# Export location.changed topic
kafka-console-consumer --topic location.changed \
  --from-beginning > location-events-backup.txt

# Import to restore
kafka-console-producer --topic location.changed < location-events-backup.txt
```

## Performance Tuning

### Database Connection Pool
```yaml
# application-prod.yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50      # Increase for high load
      minimum-idle: 10
      connection-timeout: 30000
```

### Redis Connection Pool
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 50             # Increase for concurrent queries
        max-idle: 20
        min-idle: 10
```

### Batch Configuration
```yaml
location:
  batch:
    size: 500                      # Decrease for lower latency
    window:
      ms: 100                      # Decrease for faster flushing
```

### JVM Tuning
```bash
# In Dockerfile or deployment
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
```

## Compliance & Security

### SSL/TLS Termination
```yaml
# Kubernetes ingress
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: location-service
spec:
  tls:
  - hosts:
    - location-service.example.com
    secretName: location-service-tls
```

### Network Policies
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: location-service-policy
spec:
  podSelector:
    matchLabels:
      app: location-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
```

### Pod Security Policy
```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: location-service
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
```

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Deploy Location Service

on:
  push:
    branches: [main]
    paths:
      - 'backend/location-service/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Build and test
      run: mvn clean package -f backend/location-service/pom.xml
    - name: Build Docker image
      run: docker build -t rideshare/location-service:${{ github.sha }} backend/location-service/
    - name: Push to registry
      run: docker push rideshare/location-service:${{ github.sha }}
    - name: Deploy to K8s
      run: kubectl set image deployment/location-service location-service=rideshare/location-service:${{ github.sha }}
```

## Support & Escalation

### On-Call Runbook
1. **Alert**: `location_service_down`
   - Check pod status: `kubectl get pods -n location-service`
   - Check logs: `kubectl logs -f deployment/location-service`

2. **Alert**: `location_update_lag > 30s`
   - Check input queue size: `curl /stats | jq .inputQueueSize`
   - Check Redis connectivity: `redis-cli ping`
   - Scale up if needed: `kubectl scale deployment/location-service --replicas=5`

3. **Alert**: `nearby_query_latency_p99 > 500ms`
   - Check Redis CPU: Monitor Redis node metrics
   - Check network latency: `ping redis-host`
   - Consider geographic sharding

### Escalation Contacts
- On-Call Engineer: [Slack #location-service-oncall]
- Platform Team Lead: [Slack DM]
- Database Admin: [Slack #database-support]
