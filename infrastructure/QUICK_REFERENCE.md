# Infrastructure Quick Reference Guide

## Essential Commands

### Docker Compose (Local Development)

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f <service-name>

# Execute command in container
docker-compose exec <service-name> bash

# Rebuild images
docker-compose build --no-cache
```

### Kubernetes Cluster Access

```bash
# Update kubeconfig (AWS EKS)
aws eks update-kubeconfig --name rideshare-dev-cluster --region us-east-1

# Test cluster access
kubectl cluster-info
kubectl get nodes

# View namespaces
kubectl get namespaces
```

### Deployment Operations

```bash
# View all deployments
kubectl get deployments -n microservices

# Check pod status
kubectl get pods -n microservices
kubectl describe pod <pod-name> -n microservices

# View deployment status
kubectl rollout status deployment/<service> -n microservices

# Check resource usage
kubectl top pods -n microservices
kubectl top nodes
```

### Scaling Services

```bash
# Manual scale
kubectl scale deployment <service> --replicas=5 -n microservices

# View HPA status
kubectl get hpa -n microservices
kubectl describe hpa <service>-hpa -n microservices

# Check current vs desired replicas
kubectl get deployment <service> -n microservices -o wide
```

### Debugging

```bash
# View pod logs
kubectl logs <pod-name> -n microservices
kubectl logs <pod-name> -n microservices --previous  # Crashed pod

# Stream logs
kubectl logs -f deployment/<service> -n microservices

# Execute command in pod
kubectl exec -it <pod-name> -n microservices -- bash

# Port-forward to service
kubectl port-forward svc/<service> 8080:8080 -n microservices

# Describe pod events
kubectl describe pod <pod-name> -n microservices
```

### Database Access

```bash
# PostgreSQL
kubectl port-forward svc/postgresql-primary 5432:5432 -n data-layer
psql -h localhost -U postgres -d rideshare

# Redis
kubectl port-forward svc/redis-cluster 6379:6379 -n data-layer
redis-cli -h localhost

# Kafka
kubectl port-forward svc/kafka-cluster 9092:9092 -n data-layer
kafka-console-consumer --bootstrap-server localhost:9092 --topic ride-events
```

### Monitoring Access

```bash
# Prometheus
kubectl port-forward svc/prometheus 9090:9090 -n monitoring
# Access: http://localhost:9090

# Grafana
kubectl port-forward svc/grafana 3000:3000 -n monitoring
# Access: http://localhost:3000 (admin/admin)

# Alertmanager
kubectl port-forward svc/alertmanager 9093:9093 -n monitoring
# Access: http://localhost:9093
```

## Common Troubleshooting

### Pod CrashLoopBackOff

```bash
# Check logs
kubectl logs <pod-name> -n microservices --previous

# Check pod events
kubectl describe pod <pod-name> -n microservices

# Check resource availability
kubectl top nodes
kubectl describe node <node-name>

# Check resource requests vs available
kubectl describe node <node-name> | grep -A 5 "Allocated resources"
```

### Pod Pending

```bash
# Check PVC binding
kubectl get pvc -n data-layer
kubectl describe pvc <pvc-name> -n data-layer

# Check events
kubectl get events -n data-layer --sort-by='.lastTimestamp'

# Check node capacity
kubectl describe nodes | grep -E "Name:|Allocatable"
```

### Service Connectivity Issues

```bash
# Test DNS
kubectl run debug --image=busybox --rm -it --restart=Never -- \
  nslookup auth-service.microservices.svc.cluster.local

# Test connectivity
kubectl run curl --image=curlimages/curl --rm -it --restart=Never -- \
  curl http://auth-service.microservices.svc.cluster.local:8080/actuator/health

# Check service endpoints
kubectl get endpoints <service> -n microservices
```

### Database Connection Errors

```bash
# Check PostgreSQL status
kubectl get statefulsets -n data-layer
kubectl describe pod postgresql-0 -n data-layer

# Check credentials
kubectl get secret postgres-credentials -n data-layer -o yaml | grep -A 2 password

# Test connectivity
kubectl run psql --image=postgres:15 --rm -it -- \
  psql -h postgresql-primary.data-layer.svc.cluster.local -U postgres -d rideshare -c "SELECT 1"
```

## Metric Queries

### Prometheus Queries (useful patterns)

```promql
# Request rate
rate(http_requests_total[5m])

# Latency (95th percentile)
histogram_quantile(0.95, http_request_duration_seconds)

# Error rate
rate(http_requests_total{status=~"5.."}[5m])

# Pod memory usage
container_memory_usage_bytes{pod=~".*-service"}

# Pod CPU usage
rate(container_cpu_usage_seconds_total{pod=~".*-service"}[5m])

# Database connections
pg_stat_activity_count

# Redis memory
redis_used_memory_bytes / redis_memory_limit_bytes
```

## Deployment Procedures

### Deploy to Dev (Automatic)
1. Push to `develop` branch
2. GitHub Actions CI runs automatically
3. On success, auto-deploys to dev cluster
4. Check Slack for notification

### Deploy to Staging (Manual)
1. Go to GitHub Actions
2. Select "Deploy to Staging" workflow
3. Click "Run workflow"
4. Select branch (usually main)
5. Confirm - requires approval
6. Monitor deployment in Actions log

### Deploy to Production (Manual + Approval)
1. Go to GitHub Actions
2. Select "Deploy to Production" workflow
3. Click "Run workflow"
4. Select branch (usually main with tag)
5. Confirm - **REQUIRES APPROVAL**
6. Blue-green deployment starts
7. Traffic gradually shifts 10% → 100%
8. Automated rollback if health checks fail

## Alert Response Guide

### Matching Engine Latency High

**Alert**: `MatchingEngineLatencyHigh` (P95 > 200ms)

**Response**:
1. Check dashboard: `kubectl port-forward svc/grafana 3000:3000 -n monitoring`
2. View Matching Engine dashboard
3. If CPU >60%: `kubectl scale deployment matching-engine --replicas=10 -n microservices`
4. Wait 2-3 min for pods to start
5. Monitor HPA: `kubectl describe hpa matching-engine-hpa -n microservices`
6. If still high: Check Location Service connectivity

### Location Service Throughput Low

**Alert**: `LocationServiceThroughputLow` (< 80k/sec)

**Response**:
1. Check Redis connectivity: `kubectl port-forward redis-cluster 6379:6379 -n data-layer`
2. Test: `redis-cli ping`
3. Check Kafka: `kubectl logs -f statefulsets/kafka -n data-layer`
4. Scale location-service: `kubectl scale deployment location-service --replicas=10 -n microservices`
5. Monitor: `kubectl describe hpa location-service-hpa -n microservices`

### PostgreSQL Replication Lag High

**Alert**: `PostgreSQLReplicationLag` (> 30s)

**Response**:
1. Check replication status:
   ```bash
   kubectl exec postgresql-0 -n data-layer -- \
     psql -U postgres -c "SELECT * FROM pg_stat_replication;"
   ```
2. Check network: `kubectl exec postgresql-0 -n data-layer -- ping postgresql-1.postgresql-cluster.data-layer.svc`
3. If lag persists, check standby logs: `kubectl logs postgresql-1 -n data-layer`
4. Restart standby if needed: `kubectl delete pod postgresql-1 -n data-layer`

### Redis Memory High

**Alert**: `RedisDiskSpaceWarning` (> 90%) or Critical (> 95%)

**Response**:
1. Check memory: `kubectl top pods -n data-layer | grep redis`
2. Check config: `kubectl describe configmap redis-config -n data-layer`
3. For warning: Monitor, check eviction policy
4. For critical:
   - Scale up: Add more Redis nodes
   - Or reduce maxmemory setting
   - Clear expired keys: `redis-cli FLUSHDB`

## Useful Aliases (add to ~/.bashrc)

```bash
# Kubernetes shortcuts
alias k='kubectl'
alias kgp='kubectl get pods'
alias kdp='kubectl describe pod'
alias kl='kubectl logs'
alias kex='kubectl exec -it'
alias kpf='kubectl port-forward'

# Namespace shortcuts
alias kmicro='kubectl -n microservices'
alias kdata='kubectl -n data-layer'
alias kmon='kubectl -n monitoring'

# Common operations
alias kwatch='kubectl get pods -n microservices -w'
alias ktop='kubectl top pods -n microservices'
alias kres='kubectl get deployments,statefulsets -n microservices'
```

## Service Dependencies

```
auth-service
  ↓
rider-service ──→ location-service ──→ matching-engine
driver-service ↗               ↑              ↓
                                           ride-service
                                              ↓
                              notification-service ←─ eta-service
                                      ↓
                              WebSocket (client notification)
```

## Configuration Files Overview

| File | Purpose | Customization |
|------|---------|---------------|
| `kubernetes/secrets.yaml` | Credentials, API keys | ⚠️ **MUST CUSTOMIZE** |
| `kubernetes/services/*.yaml` | Microservice deployments | HPA thresholds, replicas |
| `kubernetes/data/*.yaml` | Databases (PostgreSQL, Redis, Kafka) | Storage sizes, retention |
| `docker/docker-compose.yaml` | Local development | Service ports, volumes |
| `monitoring/prometheus/rules.yaml` | Alert thresholds | Alert thresholds, durations |
| `monitoring/grafana/dashboards/` | Visualizations | Dashboard configs |

## Regular Maintenance

### Weekly
- [ ] Check cluster health: `kubectl get nodes`
- [ ] Review error rates in Grafana
- [ ] Check PVC usage: `kubectl get pvc -A`
- [ ] Review logs for warnings

### Monthly
- [ ] Test backup/restore procedures
- [ ] Rotate API keys/secrets
- [ ] Review and update alert thresholds
- [ ] Check disk usage on nodes

### Quarterly
- [ ] Kubernetes version upgrade planning
- [ ] Database performance tuning
- [ ] Disaster recovery drill
- [ ] Capacity planning review

## Emergency Procedures

### Cluster Recovery

```bash
# If master node fails:
# Contact AWS support, they handle multi-AZ failover

# If worker node fails:
kubectl delete node <node-name>  # Mark for eviction
# Pods automatically reschedule to healthy nodes

# Check if pods are evicting:
kubectl get pods -A --field-selector=status.phase=Failed
```

### Database Recovery

```bash
# PostgreSQL recovery from WAL
# Contact DBA or follow: https://www.postgresql.org/docs/15/continuous-archiving.html

# Redis recovery (clear and rebuild)
kubectl exec redis-0 -n data-layer -- redis-cli FLUSHDB
# Data will rebuild from Kafka event log

# Kafka recovery
# 7-day retention means up to 7 days of events available
# Use event log to rebuild lost data
```

---

**Last Updated**: 2026-06-02
**Quick Links**:
- Full README: `infrastructure/README.md`
- Delivery Summary: `INFRASTRUCTURE_DELIVERY.md`
- Kubernetes Docs: https://kubernetes.io/
- Docker Docs: https://docs.docker.com/
