# Entity Resolution Library

A graph-native entity resolution library for Java 21, built on [FalkorDB](https://www.falkordb.com/). Provides deterministic and probabilistic deduplication, explicit synonym modeling, audit-safe merges, and optional LLM-assisted semantic enrichment.

## Features

- **Entity Resolution** -- Deduplicate entities using exact matching, fuzzy matching (Levenshtein, Jaro-Winkler, Jaccard), and optional LLM-assisted semantic matching
- **Synonym Management** -- First-class synonym modeling with confidence scores and provenance tracking
- **Merge Safety** -- Opaque `EntityReference` handles that remain valid after merges
- **Batch Processing** -- Efficient bulk ingestion with automatic deduplication
- **Audit Trail** -- Immutable merge ledger for compliance and debugging
- **REST API** -- Jakarta RS endpoints with API key authentication, role-based access control, and OpenAPI specification
- **Quarkus Integration** -- CDI producers and YAML configuration for drop-in Quarkus deployment
- **LLM Integration** -- Optional Ollama integration for semantic entity matching (e.g., "Big Blue" to IBM)

## Requirements

- Java 21+
- FalkorDB 4.0+ (or Redis with RedisGraph module)
- Ollama (optional, for LLM-assisted matching)

## Quick Start

### As a Java Library

```xml
<dependency>
    <groupId>com.entity.resolution</groupId>
    <artifactId>entity-resolution</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
EntityResolver resolver = EntityResolver.builder()
    .falkorDB("localhost", 6379, "knowledge-graph")
    .build();

EntityResolutionResult result = resolver.resolve("Microsoft Corp", EntityType.COMPANY);
EntityReference ref = result.getEntityReference();
```

### As a Quarkus REST Service

Add the dependency to your Quarkus project:

```xml
<dependency>
    <groupId>com.entity.resolution</groupId>
    <artifactId>entity-resolution</artifactId>
    <version>1.0.0</version>
</dependency>
```

Add `application.yaml` to `src/main/resources/`:

```yaml
entity-resolution:
  falkordb:
    host: localhost
    port: 6379
    graph-name: my-knowledge-graph

  resolution:
    auto-merge-threshold: 0.92
    synonym-threshold: 0.80
    review-threshold: 0.60

  llm:
    enabled: false          # set true to activate LLM matching

  security:
    enabled: true
    api-key-header: X-API-Key
    admin-keys:
      - "your-admin-api-key"
    writer-keys:
      - "your-writer-api-key"
    reader-keys:
      - "your-reader-api-key"
```

The CDI producer (`EntityResolutionProducer`) auto-discovers in Quarkus and wires:
- `EntityResolver` -- from FalkorDB connection pool + resolution options
- `ReviewService` -- for the manual review queue
- Security filters -- API key auth, role authorization, CORS, rate limiting
- REST resources -- `/api/v1/entities` and `/api/v1/reviews`

Start Quarkus and the REST API is live:

```bash
mvn quarkus:dev
```

## REST API

All endpoints require an API key in the `X-API-Key` header.

### Entity Resolution

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/entities/resolve` | WRITER | Resolve a single entity |
| `POST` | `/api/v1/entities/batch` | WRITER | Batch resolve entities |
| `GET` | `/api/v1/entities/{id}` | READER | Get entity by ID |
| `GET` | `/api/v1/entities/{id}/synonyms` | READER | Get entity synonyms |
| `POST` | `/api/v1/entities/relationships` | WRITER | Create a relationship |
| `GET` | `/api/v1/entities/{id}/relationships` | READER | Get entity relationships |
| `GET` | `/api/v1/entities/{id}/audit` | READER | Get audit trail |
| `GET` | `/api/v1/entities/{id}/merge-history` | READER | Get merge history |

### Manual Review

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/reviews` | READER | List pending reviews |
| `GET` | `/api/v1/reviews/count` | READER | Count pending reviews |
| `GET` | `/api/v1/reviews/{id}` | READER | Get review item |
| `POST` | `/api/v1/reviews/{id}/approve` | ADMIN | Approve review (triggers merge) |
| `POST` | `/api/v1/reviews/{id}/reject` | ADMIN | Reject review |

### Example: Resolve an Entity

```bash
curl -X POST http://localhost:8080/api/v1/entities/resolve \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-writer-api-key" \
  -d '{
    "name": "Microsoft Corporation",
    "entityType": "COMPANY"
  }'
```

Response:

```json
{
  "entityId": "550e8400-e29b-41d4-a716-446655440000",
  "canonicalName": "Microsoft Corporation",
  "normalizedName": "microsoft",
  "entityType": "COMPANY",
  "status": "ACTIVE",
  "isNewEntity": true,
  "wasMerged": false,
  "wasMatchedViaSynonym": false,
  "wasNewSynonymCreated": false,
  "matchConfidence": 1.0
}
```

### Example: Batch Resolution

```bash
curl -X POST http://localhost:8080/api/v1/entities/batch \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-writer-api-key" \
  -d '{
    "items": [
      { "name": "Acme Corp", "entityType": "COMPANY" },
      { "name": "ACME Corporation", "entityType": "COMPANY" },
      { "name": "John Smith", "entityType": "PERSON" }
    ]
  }'
```

### OpenAPI / Swagger

When running in Quarkus with `quarkus-smallrye-openapi`, the OpenAPI spec is auto-generated from the MicroProfile OpenAPI annotations:

```bash
# OpenAPI JSON spec
curl http://localhost:8080/q/openapi

# Swagger UI (if quarkus-smallrye-openapi-ui is on classpath)
open http://localhost:8080/q/swagger-ui
```

## Security

### Authentication

All REST endpoints are protected by API key authentication. Keys are configured in `application.yaml` under `entity-resolution.security`.

The API key must be sent in the `X-API-Key` header (configurable via `api-key-header`).

### Authorization (Roles)

Keys are mapped to three hierarchical roles:

| Role | Access |
|------|--------|
| **READER** | GET endpoints: entity lookup, synonyms, relationships, audit trail, reviews |
| **WRITER** | READER + POST resolve, batch, create relationships |
| **ADMIN** | WRITER + approve/reject reviews (triggers irreversible merges) |

### CORS

Cross-origin requests are handled by the built-in CORS filter:

```yaml
entity-resolution:
  cors:
    enabled: true
    allowed-origins: "https://dashboard.example.com"
    allowed-methods: GET,POST,OPTIONS
    allowed-headers: Content-Type,X-API-Key
    max-age: 86400
```

### Rate Limiting

Per-API-key rate limiting using a token bucket algorithm:

```yaml
entity-resolution:
  rate-limit:
    enabled: true
    requests-per-second: 100
    burst-size: 200
```

## Configuration Reference

All properties use the prefix `entity-resolution.*` and can be overridden via environment variables (e.g., `ENTITY_RESOLUTION_FALKORDB_HOST`).

| Property | Default | Description |
|----------|---------|-------------|
| `falkordb.host` | `localhost` | FalkorDB host |
| `falkordb.port` | `6379` | FalkorDB port |
| `falkordb.graph-name` | `entity-resolution` | Graph name |
| `pool.max-total` | `20` | Max pool connections |
| `pool.max-idle` | `10` | Max idle connections |
| `pool.min-idle` | `2` | Min idle connections |
| `resolution.auto-merge-threshold` | `0.92` | Score >= this triggers auto-merge |
| `resolution.synonym-threshold` | `0.80` | Score >= this creates synonym |
| `resolution.review-threshold` | `0.60` | Score >= this flags for review |
| `resolution.auto-merge-enabled` | `true` | Enable/disable auto-merge |
| `cache.enabled` | `true` | Enable resolution cache |
| `cache.max-size` | `10000` | Max cache entries |
| `cache.ttl-seconds` | `300` | Cache entry TTL |
| `llm.enabled` | `false` | Enable LLM enrichment |
| `llm.provider` | `ollama` | LLM provider type |
| `llm.confidence-threshold` | `0.85` | Min LLM confidence |
| `llm.ollama.base-url` | `http://localhost:11434` | Ollama URL |
| `llm.ollama.model` | `llama3.2` | Ollama model |
| `llm.ollama.timeout-seconds` | `30` | Ollama request timeout |
| `security.enabled` | `true` | Enable API key auth |
| `security.api-key-header` | `X-API-Key` | Header name for API key |
| `cors.enabled` | `true` | Enable CORS |
| `rate-limit.enabled` | `true` | Enable rate limiting |
| `rate-limit.requests-per-second` | `100` | Sustained rate per key |
| `rate-limit.burst-size` | `200` | Burst capacity |

See [`src/main/resources/application.yaml`](src/main/resources/application.yaml) for the complete reference configuration.

## Library API

### Fuzzy Matching

```java
EntityResolutionResult result = resolver.resolve(
    "Microsft Corporation",  // typo
    EntityType.COMPANY,
    ResolutionOptions.builder()
        .autoMergeThreshold(0.92)
        .synonymThreshold(0.80)
        .build()
);

if (result.wasNewSynonymCreated()) {
    System.out.println("Created synonym: " + result.getInputName()
        + " -> " + result.getMatchedName());
}
```

### Relationships

```java
EntityReference company = resolver.resolve("Acme Corp", EntityType.COMPANY)
    .getEntityReference();
EntityReference ceo = resolver.resolve("John Smith", EntityType.PERSON)
    .getEntityReference();

// Library-managed relationships auto-migrate during merges
Relationship rel = resolver.createRelationship(company, ceo, "HAS_CEO");
```

### Batch Processing

```java
try (BatchContext batch = resolver.beginBatch()) {
    EntityReference a = batch.resolve("Company A", EntityType.COMPANY).getEntityReference();
    EntityReference b = batch.resolve("Company B", EntityType.COMPANY).getEntityReference();
    batch.createRelationship(a, b, "PARTNER");

    BatchResult result = batch.commit();
    System.out.println("Created " + result.newEntitiesCreated() + " entities");
}
```

### LLM-Assisted Matching (Ollama)

```java
OllamaLLMProvider llmProvider = OllamaLLMProvider.builder()
    .baseUrl("http://localhost:11434")
    .model("llama3.2")
    .build();

EntityResolver resolver = EntityResolver.builder()
    .graphConnection(connection)
    .llmProvider(llmProvider)
    .options(ResolutionOptions.withLLM())
    .build();

// "Big Blue" will match "IBM"
EntityResolutionResult result = resolver.resolve("Big Blue", EntityType.COMPANY);
```

### Multi-Tenancy

```java
// Standard thread-based tenancy
try (var scope = TenantContext.scoped("tenant-123")) {
    resolver.resolve("Acme Corp", EntityType.COMPANY);
}

// Virtual thread / executor propagation
TenantContext.setTenant("tenant-123");
executor.submit(TenantContext.propagate(() -> {
    resolver.resolve("Acme Corp", EntityType.COMPANY);
}));
```

## Match Decisions

| Score Range | Decision | Action |
|-------------|----------|--------|
| >= 0.92 | AUTO_MERGE | Automatically merge entities |
| 0.80 - 0.92 | SYNONYM_ONLY | Create synonym link |
| 0.60 - 0.80 | REVIEW | Flag for manual review |
| < 0.60 | NO_MATCH | Create new entity |

## Graph Data Model

```cypher
(:Entity {id, canonicalName, normalizedName, type, confidenceScore, status, createdAt, updatedAt})
(:Synonym {id, value, normalizedValue, source, confidence, createdAt})
(:Synonym)-[:SYNONYM_OF]->(:Entity)
(:Entity {status: "MERGED"})-[:MERGED_INTO]->(:Entity {status: "ACTIVE"})
(:Entity)-[:LIBRARY_REL {type: String}]->(:Entity)
```

## Building from Source

```bash
# Build
mvn clean install

# Run unit tests (excludes integration tests)
mvn test

# Run integration tests (requires Docker for Testcontainers)
mvn verify -Pintegration-test

# Build JMH benchmark uber-jar
mvn package -Pbenchmark -DskipTests
```

## License

Apache License 2.0 -- see [LICENSE](LICENSE).

## Contributing

Contributions welcome. Please submit a Pull Request.
