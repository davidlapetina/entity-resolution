# Deployment Guide

## System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Java | 21 (LTS) | 21+ |
| FalkorDB | 4.0+ | Latest stable |
| Memory (JVM) | 512 MB | 2+ GB |
| CPU | 2 cores | 4+ cores |
| Docker | 20.10+ | Latest stable (for Testcontainers) |

## Quick Start

### 1. Start FalkorDB

```bash
# Docker (simplest)
docker run -p 6379:6379 -d falkordb/falkordb:latest

# Docker with persistent storage
docker run -p 6379:6379 -v falkordb-data:/data -d falkordb/falkordb:latest

# Verify FalkorDB is running
docker exec -it <container_id> redis-cli PING
# Expected: PONG
```

### 2. Add Library Dependency

```xml
<dependency>
    <groupId>com.entity.resolution</groupId>
    <artifactId>entity-resolution</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

### 3. Initialize the Resolver

```java
EntityResolver resolver = EntityResolver.builder()
    .falkorDB("localhost", 6379, "my-graph")
    .build();

// Verify connection
HealthStatus health = resolver.health();
System.out.println("Status: " + health.status());
```

## Configuration Reference

### Core Resolution Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `autoMergeThreshold` | double | 0.92 | Minimum score for automatic merge |
| `synonymThreshold` | double | 0.80 | Minimum score for synonym creation |
| `reviewThreshold` | double | 0.60 | Minimum score for manual review |
| `autoMergeEnabled` | boolean | true | Enable/disable automatic merging |
| `useLLM` | boolean | false | Enable LLM-assisted enrichment |
| `llmConfidenceThreshold` | double | 0.85 | LLM confidence threshold |
| `sourceSystem` | String | "SYSTEM" | Source system identifier for audit |
| `confidenceDecayLambda` | double | 0.001 | Exponential decay rate for synonym confidence (v1.1) |
| `reinforcementCap` | double | 0.15 | Max confidence boost from support count (v1.1) |

### Batch Processing Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maxBatchSize` | int | 10,000 | Maximum entities per batch |
| `batchCommitChunkSize` | int | 1,000 | Entities per commit chunk |
| `maxBatchMemoryBytes` | long | 104,857,600 | Maximum batch memory (100 MB) |

### Cache Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cachingEnabled` | boolean | false | Enable resolution caching |
| `cacheMaxSize` | int | 10,000 | Maximum cache entries |
| `cacheTtlSeconds` | int | 300 | Cache entry time-to-live |

### Concurrency Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `lockTimeoutMs` | long | 5,000 | Distributed lock timeout |
| `asyncTimeoutMs` | long | 30,000 | Async operation timeout |

### Connection Pool Options (PoolConfig)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maxTotal` | int | 10 | Maximum active connections |
| `maxIdle` | int | 5 | Maximum idle connections |
| `minIdle` | int | 1 | Minimum idle connections |
| `maxWaitMs` | long | 5,000 | Max wait for connection |

### Similarity Weights

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `levenshteinWeight` | double | 0.40 | Levenshtein distance weight |
| `jaroWinklerWeight` | double | 0.35 | Jaro-Winkler weight |
| `jaccardWeight` | double | 0.25 | Jaccard similarity weight |

## Docker Deployment

### Docker Compose

```yaml
version: '3.8'
services:
  falkordb:
    image: falkordb/falkordb:latest
    ports:
      - "6379:6379"
    volumes:
      - falkordb-data:/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "PING"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  falkordb-data:
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: falkordb
spec:
  replicas: 1
  selector:
    matchLabels:
      app: falkordb
  template:
    metadata:
      labels:
        app: falkordb
    spec:
      containers:
        - name: falkordb
          image: falkordb/falkordb:latest
          ports:
            - containerPort: 6379
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2"
          livenessProbe:
            exec:
              command: ["redis-cli", "PING"]
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            exec:
              command: ["redis-cli", "PING"]
            initialDelaySeconds: 5
            periodSeconds: 5
          volumeMounts:
            - name: data
              mountPath: /data
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: falkordb-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: falkordb
spec:
  selector:
    app: falkordb
  ports:
    - port: 6379
      targetPort: 6379
  type: ClusterIP
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: falkordb-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
```

### Application Configuration for Kubernetes

```java
String host = System.getenv().getOrDefault("FALKORDB_HOST", "falkordb");
int port = Integer.parseInt(System.getenv().getOrDefault("FALKORDB_PORT", "6379"));
String graphName = System.getenv().getOrDefault("GRAPH_NAME", "entity-resolution");

EntityResolver resolver = EntityResolver.builder()
    .falkorDB(host, port, graphName)
    .options(ResolutionOptions.builder()
        .cachingEnabled(true)
        .cacheMaxSize(10000)
        .build())
    .build();
```

## Optional Dependencies

The library has several optional dependencies that enable additional features:

| Dependency | Feature | Required? |
|------------|---------|-----------|
| `caffeine` | In-memory caching | No (falls back to no-op cache) |
| `micrometer-core` | Metrics collection | No (falls back to no-op metrics) |
| `opentelemetry-api` | Distributed tracing | No (falls back to no-op tracing) |
| `jackson-databind` | JSON for LLM integration | Only if using LLM features |
| `jakarta.ws.rs-api` | REST API module | Only if using REST endpoints |
| `logback-classic` | Logging implementation | No (any SLF4J binding works) |

Include only the dependencies you need:

```xml
<!-- Enable caching -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>

<!-- Enable metrics -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <version>1.12.2</version>
</dependency>

<!-- Enable tracing -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.34.1</version>
</dependency>
```
