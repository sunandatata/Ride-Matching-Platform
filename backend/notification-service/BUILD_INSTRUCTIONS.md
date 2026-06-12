# Notification Service - Build and Deployment Instructions

## Quick Start

### Prerequisites
- Java 21 LTS
- Maven 3.8+
- Kafka 3.0+ running on localhost:9092
- Redis 6.0+ running on localhost:6379

### Build
```bash
cd backend
mvn clean package -pl notification-service
```

### Run Development
```bash
# Terminal 1: Start the service
cd backend
mvn spring-boot:run -pl notification-service

# Service runs on http://localhost:8081
# WebSocket endpoint: ws://localhost:8081/api/v1/ws
# Metrics endpoint: http://localhost:8082/actuator/metrics
```

### Run Tests
```bash
cd backend
# Unit tests
mvn test -pl notification-service

# With coverage report
mvn test -pl notification-service jacoco:report

# View coverage: target/site/jacoco/index.html
```

## Docker Build

```dockerfile
# FROM openjdk:21-slim
# COPY notification-service-1.0.0.jar app.jar
# ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build image
docker build -t notification-service:1.0.0 .

# Run container
docker run -p 8081:8081 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e REDIS_HOST=redis \
  notification-service:1.0.0
```

## Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: notification-service
  template:
    metadata:
      labels:
        app: notification-service
    spec:
      containers:
      - name: notification-service
        image: notification-service:1.0.0
        ports:
        - containerPort: 8081
          name: websocket
        - containerPort: 8082
          name: metrics
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: kafka-broker-1:9092,kafka-broker-2:9092,kafka-broker-3:9092
        - name: REDIS_HOST
          valueFrom:
            secretKeyRef:
              name: redis-secrets
              key: host
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-secrets
              key: password
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8082
          initialDelaySeconds: 10
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"

---
apiVersion: v1
kind: Service
metadata:
  name: notification-service
spec:
  type: LoadBalancer
  ports:
  - port: 8081
    targetPort: 8081
    name: websocket
  selector:
    app: notification-service
```

## Configuration

### Development (application.yaml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  data:
    redis:
      host: localhost
      port: 6379
server:
  port: 8081
```

### Production (application-prod.yaml)
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
  data:
    redis:
      host: ${REDIS_HOST}
      password: ${REDIS_PASSWORD}
      ssl: true
```

## Verifying Deployment

### Health Checks
```bash
# Liveness
curl http://localhost:8082/actuator/health/liveness

# Readiness
curl http://localhost:8082/actuator/health/readiness
```

### Metrics
```bash
# All metrics
curl http://localhost:8082/actuator/metrics

# Prometheus format
curl http://localhost:8082/actuator/prometheus

# Active connections
curl http://localhost:8082/actuator/metrics/websocket.connections.active
```

### WebSocket Connection Test
```bash
# Using websocat or wscat
wscat -c ws://localhost:8081/api/v1/ws

# Send subscription
> {"type":"subscribe","ride_id":"test-ride-123"}

# Send ping
> {"type":"ping"}

# Receive events
< {"message_id":"...","type":"driver.location_updated","ride_id":"test-ride-123","timestamp":"2026-06-02T14:30:00Z","data":{...}}
```

## Troubleshooting

### Connection Refused
- Check Kafka is running: `netstat -an | grep 9092`
- Check Redis is running: `redis-cli ping`

### No Messages Delivered
- Verify Kafka topics exist: `kafka-topics.sh --list --bootstrap-server localhost:9092`
- Check Kafka consumer group: `kafka-consumer-groups.sh --group notification-service --describe --bootstrap-server localhost:9092`

### High Memory Usage
- Increase JVM heap: `java -Xmx2g -jar notification-service.jar`
- Check for connection leaks in logs

### Slow Message Delivery
- Monitor Redis latency: `redis-cli --latency`
- Check WebSocket client connection count: curl metrics endpoint

## Production Deployment Checklist

- [ ] Redis cluster configured and replicated
- [ ] Kafka topics created with proper replication factor (3+)
- [ ] SSL certificates installed and configured
- [ ] Environment variables set correctly
- [ ] Prometheus scraping configured
- [ ] Grafana dashboards created
- [ ] Alerting rules configured
- [ ] Health checks passing in K8s
- [ ] Load testing completed (100+ concurrent connections)
- [ ] Rollback plan documented
- [ ] On-call runbook created

## Monitoring

### Key Metrics to Track
1. `websocket.connections.active` - Current active connections
2. `websocket.message.delivery.latency` - Message delivery time
3. `websocket.connections.reconnected` - Reconnection rate
4. `websocket.messages.duplicates` - Duplicate detection rate

### Alerts to Configure
1. No active connections (might indicate service down)
2. High latency (>200ms p99)
3. High duplicate rate (>5%)
4. Kafka consumer lag increasing
5. Redis connection pool exhaustion

## Support

For issues or questions:
1. Check README.md for API documentation
2. Check ARCHITECTURE.md for design details
3. Review logs for error messages
4. Run integration tests to verify setup
