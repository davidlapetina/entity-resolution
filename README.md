# Entity Resolution Library

A graph-native entity resolution library for Java 21, built on [FalkorDB](https://www.falkordb.com/). Provides deterministic and probabilistic deduplication, explicit synonym modeling, audit-safe merges, and optional LLM-assisted semantic enrichment.

## Why This Project?

### The Core Problem

Every organization that ingests data from multiple sources faces the same silent corruption: **the same real-world entity appears under different names, spellings, and abbreviations**. "Microsoft Corp", "Microsoft Corporation", "MSFT", "Microsft" (typo), and "Big Blue's competitor" all refer to the same company. Without resolution, your data is fragmented -- your knowledge graph has five nodes where it should have one, your analytics double-count, and your AI models train on noise.

Traditional deduplication tools treat this as a batch ETL problem. This library treats it as a **live, auditable, graph-native operation** -- and that distinction matters.

### Centralized Synonym Registry with Provenance

The most distinctive design choice is modeling synonyms as **first-class graph nodes with confidence scores and provenance**, not as a flat lookup table.

When the system encounters "MSFT" and resolves it to "Microsoft Corporation" at 0.87 confidence via Jaro-Winkler similarity, that fact is recorded: the synonym node stores _who_ created it (system, LLM, or human reviewer), _when_, and _how confident_ the match was. This is fundamentally different from a static alias table because:

- **Confidence is queryable.** You can ask: "show me all synonyms below 0.85 confidence" and surface the weakest links for human review. The system self-documents its own uncertainty.
- **Provenance is traceable.** Regulated industries (finance, healthcare, government) require audit trails for entity decisions. If two companies were merged in your graph, you need to know why, when, and by whose authority. The immutable merge ledger provides exactly this.
- **Synonyms accumulate organically.** Each new data source that mentions "MS Corp" strengthens the synonym graph. Over time, the system becomes an institutional memory of how your organization refers to entities -- a centralized, versioned dictionary that no single team could build manually.

This makes the library not just a deduplication engine but a **living knowledge base of entity identity** -- one that improves with every ingestion.

### Why a Graph Database

Entity resolution is inherently a graph problem. Consider what happens when you merge Entity A into Entity B:

- Every relationship pointing to A must be migrated to B
- Every synonym of A becomes a synonym of B
- Every entity that was _already_ merged into A must now transitively point to B
- Every downstream system holding a reference to A must resolve to B

In a relational database, this requires updating foreign keys across multiple tables, handling cascades, and hoping no constraint violations occur. In a graph database, this is a **local traversal**: follow edges from A, rewire them to B, mark A as `MERGED`. The data model maps directly to the operation.

More importantly, **the graph IS the context**. When an LLM needs to decide whether "Big Blue" refers to IBM or to a paint company, the relevant context is: what other entities are nearby in the graph? What relationships exist? What synonyms have already been established? A graph database answers these questions with a single traversal, while a relational database would require multiple joins across normalized tables.

The graph also naturally represents the **merge tree** -- the full history of which entities were absorbed into which. This tree is not just an audit artifact; it is the mechanism by which `EntityReference` handles remain valid after merges. When you hold a reference to an entity that was later merged, the library follows the `MERGED_INTO` edges to find the current canonical node. This is a graph traversal, not a table scan.

### Why FalkorDB

FalkorDB is a deliberate choice over Neo4j, Amazon Neptune, or JanusGraph for several reasons:

1. **Redis-compatible protocol.** FalkorDB speaks the Redis wire protocol. This means every Redis client library, every Redis monitoring tool, every Redis deployment pattern (Sentinel, Cluster, managed services) works out of the box. There is no new infrastructure to learn. If your team knows Redis -- and almost every team does -- they know how to operate FalkorDB. Connection pooling, health checks, and failover use the same patterns as any Redis deployment.

2. **Performance profile.** FalkorDB stores graphs in compressed sparse matrices (GraphBLAS) and executes Cypher queries using linear algebra operations. For the access patterns entity resolution requires -- exact lookups by normalized name, blocking key scans, relationship traversals during merge -- this yields sub-millisecond latency at scales where disk-based graph databases would require index warming. The entire working set fits in memory, which is exactly right for an entity resolution index.

3. **Operational simplicity.** No JVM tuning, no cluster topology planning, no separate storage engine. A single FalkorDB process handles the graph. For teams that already run Redis, adding FalkorDB is a configuration change, not an infrastructure project.

4. **Embeddability.** Because the library communicates via Redis protocol, it can target FalkorDB running as a sidecar, as a managed cloud service, or as an embedded process in test (via Testcontainers). The integration tests in this project demonstrate all three modes.

### Why LLM Integration Changes the Game

Traditional entity resolution relies on string similarity algorithms -- Levenshtein, Jaro-Winkler, Jaccard. These work well for typos ("Microsft" to "Microsoft") and abbreviations ("Corp" to "Corporation") but fail entirely for **semantic equivalences**: "Big Blue" to "IBM", "The Fruit Company" to "Apple Inc", "Alphabet" to "Google's parent company".

The LLM integration in this library is designed with a critical safety constraint: **LLMs suggest, they never decide**. The LLM produces a confidence score and reasoning, but the merge/synonym decision is made by the same threshold logic that governs fuzzy matching. This means:

- LLM hallucinations don't corrupt your graph. A confident but wrong LLM response is treated the same as a high fuzzy score -- it may trigger a synonym or a review queue item, but never an unsupervised destructive merge.
- The LLM's reasoning is stored in the audit trail, so you can inspect _why_ it thought "Big Blue" matched "IBM" and decide if you trust that reasoning.
- The LLM provider is pluggable. The library ships with an Ollama provider for local/private deployment, but the `LLMProvider` interface accepts any backend -- OpenAI, Anthropic, a fine-tuned model, or a domain-specific classifier.

The graph database amplifies the LLM's value here. When the LLM evaluates whether two entities match, the system can feed it the **entity's neighborhood** as context: existing synonyms, connected entities, entity types. "Big Blue" alone is ambiguous; "Big Blue" in a graph where it neighbors "Armonk, NY" and "mainframe" is clearly IBM.

### Additional Architectural Strengths

**Opaque references survive merges.** The `EntityReference` pattern solves a problem that most deduplication systems ignore: what happens to the IDs your application already stored? If you resolved "Acme Corp" yesterday and got ID `abc-123`, and today "Acme Corporation" merges into the same entity with ID `def-456`, your stored `abc-123` still works. The reference follows the merge chain transparently. This eliminates an entire class of stale-reference bugs.

**The review queue bridges automation and judgment.** Not every match decision should be automated. The three-tier threshold system (auto-merge / synonym / review) creates a natural workflow: high-confidence matches are handled instantly, medium-confidence matches create synonyms for future resolution, and ambiguous matches enter a queue for human decision. This is pragmatic -- it acknowledges that no algorithm is perfect while still automating the 80% of cases that are straightforward.

**Multi-tenancy via graph isolation.** Each tenant gets a separate graph name in FalkorDB, providing complete data isolation without separate database instances. The `TenantContext` propagation ensures tenant identity follows the request through async operations and virtual threads -- a detail that most multi-tenant libraries get wrong.

**Batch processing with intra-batch deduplication.** When ingesting 10,000 records, the batch context deduplicates within the batch itself before touching the database. If your CSV contains "Acme Corp" on row 5 and "ACME Corporation" on row 500, the batch resolves them against each other without 10,000 individual round-trips to FalkorDB.

### Where This Goes Next

The architecture naturally extends to several high-value directions:

- **Cross-type resolution.** An entity of type COMPANY named "Apple" and an entity of type PRODUCT named "Apple iPhone" have a latent relationship. The graph can surface these cross-type connections for enrichment.
- **Confidence decay.** _(Now implemented in v1.1.)_ Synonym confidence degrades over time if not reinforced by new data, surfacing stale mappings for re-evaluation.
- **Federated resolution.** Multiple instances could share a FalkorDB cluster, each contributing synonyms from their domain, building a collective entity dictionary across an organization.
- **Graph-powered RAG.** The entity graph is a natural retrieval source for Retrieval-Augmented Generation. When a user asks about "Big Blue's Q3 earnings", the entity graph resolves "Big Blue" to "IBM" and retrieves IBM's subgraph as context for the LLM.
- **Active learning loop.** Human decisions in the review queue can be fed back as training signal for a fine-tuned matching model, gradually reducing the review queue volume.

## Features

- **Entity Resolution** -- Deduplicate entities using exact matching, fuzzy matching (Levenshtein, Jaro-Winkler, Jaccard), and optional LLM-assisted semantic matching
- **Synonym Management** -- First-class synonym modeling with confidence scores and provenance tracking
- **Merge Safety** -- Opaque `EntityReference` handles that remain valid after merges
- **Batch Processing** -- Efficient bulk ingestion with automatic deduplication
- **Audit Trail** -- Immutable merge ledger for compliance and debugging
- **REST API** -- Jakarta RS endpoints with API key authentication, role-based access control, and OpenAPI specification
- **Quarkus Integration** -- CDI producers and YAML configuration for drop-in Quarkus deployment
- **LLM Integration** -- Optional Ollama integration for semantic entity matching (e.g., "Big Blue" to IBM)
- **Explainable Decisions** -- Every match evaluation produces an immutable `MatchDecision` node recording score breakdowns, thresholds, and outcomes
- **Confidence Decay & Reinforcement** -- Synonym confidence decays exponentially over time and is boosted logarithmically by repeated observations
- **Human-as-Signal** -- Review approve/reject decisions are persisted as `ReviewDecision` nodes and feed back into synonym confidence

## Release Notes

### v1.1.1 -- Use Case Gap Features

**Metadata Entity Types**
- Added 7 new entity types for data governance and metadata use cases: `DATASET`, `TABLE`, `SCHEMA`, `DOMAIN`, `SERVICE`, `API`, `DOCUMENT`
- New `MetadataNormalizationRules` class with rules for version suffixes (`_v1`, `_final`), date suffixes (`_2024`), environment suffixes (`-dev`, `-prod`), and schema prefixes (`dbo.`, `public.`)

**Document Linking**
- New `linkDocument(entity, documentId, documentName, metadata)` method creates DOCUMENT entities and DOCUMENT_LINK relationships
- New `getLinkedDocuments(entity)` retrieves all documents linked to an entity

**Entity Context Endpoint**
- New `getEntityContext(entityId)` returns entity + synonyms + relationships + decisions + merge history in a single call
- New REST endpoint: `GET /api/v1/entities/{id}/context`

**Entity Subgraph Export for RAG**
- New `exportEntitySubgraph(entityId, depth)` exports the entity neighborhood for RAG pipelines (depth 1-3)
- Includes root entity, synonyms, relationships, related entities at depth, and match decisions
- New REST endpoint: `GET /api/v1/entities/{id}/subgraph?depth=1`

**MCP Tools Module**
- New `EntityResolutionMcpTools` class builds 5 read-only MCP tool definitions for LLM agents
- Tools: `resolve_entity`, `get_entity_context`, `get_entity_decisions`, `search_entities`, `get_entity_synonyms`
- All tools are read-only -- LLMs cannot create, merge, or mutate entity data

**Documentation**
- Updated `usecases.md` with concrete Java and REST code examples for each use case

### v1.1.0 -- Explainability & Learning

**Explainable Match Decisions (Decision Graph)**
- Every fuzzy match evaluation now produces an immutable `MatchDecision` node in the graph, recording full score breakdowns (Levenshtein, Jaro-Winkler, Jaccard, optional LLM), threshold snapshots, and the outcome (AUTO_MERGE, SYNONYM, REVIEW, NO_MATCH)
- Decision nodes are linked to input and candidate entities via `EVALUATED_INPUT` and `EVALUATED_CANDIDATE` edges
- New REST endpoint: `GET /api/v1/entities/{id}/decisions` returns all match decisions involving an entity

**Confidence Decay & Reinforcement Engine**
- Synonym confidence now decays exponentially over time: `effective = base * exp(-lambda * days) + reinforcementBoost(supportCount)`
- Synonyms are automatically reinforced (support count incremented) when re-encountered during resolution
- Logarithmic reinforcement boost with configurable cap prevents runaway confidence inflation
- New `Synonym` fields: `lastConfirmedAt`, `supportCount`
- Configurable via `confidence.decay-lambda` (default 0.001) and `confidence.reinforcement-cap` (default 0.15)

**Immutable Human Review Decisions (Human-as-Signal)**
- Approve/reject actions now produce immutable `ReviewDecision` nodes in the graph
- Review decisions are linked to the original `MatchDecision` via a `CONFIRMS` edge
- Approvals trigger synonym reinforcement; rejections apply negative reinforcement (confidence reduction)
- New REST endpoint: `GET /api/v1/reviews/{id}/decision` returns the review decision for a review item

**Backward Compatibility**
- All v1.0 APIs continue to work unchanged; new features are additive
- Existing graphs without decision nodes work seamlessly -- decision tracking is optional
- Default decay parameters produce negligible confidence change for short-lived deployments

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
    <version>1.1.0</version>
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
    <version>1.1.0</version>
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
| `GET` | `/api/v1/entities/{id}/decisions` | READER | Get match decisions |
| `GET` | `/api/v1/entities/{id}/context` | READER | Get entity context (synonyms, relationships, decisions, merge history) |
| `GET` | `/api/v1/entities/{id}/subgraph?depth=1` | READER | Export entity subgraph for RAG (depth 1-3) |

### Manual Review

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/reviews` | READER | List pending reviews |
| `GET` | `/api/v1/reviews/count` | READER | Count pending reviews |
| `GET` | `/api/v1/reviews/{id}` | READER | Get review item |
| `POST` | `/api/v1/reviews/{id}/approve` | ADMIN | Approve review (triggers merge) |
| `POST` | `/api/v1/reviews/{id}/reject` | ADMIN | Reject review |
| `GET` | `/api/v1/reviews/{id}/decision` | READER | Get review decision |

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

Cross-origin requests are handled by the built-in CORS filter. CORS is disabled by default; enable it with specific origins for browser-based clients:

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
| `cors.enabled` | `false` | Enable CORS (disabled by default; set specific origins when enabling) |
| `rate-limit.enabled` | `true` | Enable rate limiting |
| `rate-limit.requests-per-second` | `100` | Sustained rate per key |
| `rate-limit.burst-size` | `200` | Burst capacity |
| `confidence.decay-lambda` | `0.001` | Exponential decay rate for synonym confidence |
| `confidence.reinforcement-cap` | `0.15` | Max confidence boost from repeated observations |

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

## Explainability & Learning (v1.1)

### Decision Graph

Every fuzzy match evaluation produces an immutable `MatchDecision` node in the graph. This node records the full score breakdown (Levenshtein, Jaro-Winkler, Jaccard, optional LLM score), the threshold snapshot at evaluation time, and the outcome (AUTO_MERGE, SYNONYM, REVIEW, or NO_MATCH). Decision nodes are linked to both the input and candidate entities via `EVALUATED_INPUT` and `EVALUATED_CANDIDATE` edges.

```java
// Query all match decisions involving an entity
List<MatchDecisionRecord> decisions = resolver.getDecisionsForEntity(entityId);

for (MatchDecisionRecord d : decisions) {
    System.out.printf("Candidate: %s, Score: %.3f (%s), Outcome: %s%n",
        d.getCandidateEntityId(), d.getFinalScore(),
        d.getEvaluator(), d.getOutcome());
}
```

### Confidence Decay & Reinforcement

Synonym confidence decays exponentially over time using the formula:

```
effectiveConfidence = baseConfidence * exp(-lambda * daysSinceLastConfirmed) + reinforcementBoost(supportCount)
```

The reinforcement boost uses a logarithmic curve capped at a configurable maximum, providing diminishing returns for repeated observations. This means frequently-seen synonyms maintain high confidence while stale synonyms naturally surface for re-evaluation.

```yaml
entity-resolution:
  confidence:
    decay-lambda: 0.001          # Decay rate (higher = faster decay)
    reinforcement-cap: 0.15      # Max boost from repeated observations
```

When a synonym is re-encountered during resolution, it is automatically reinforced (support count incremented, timestamp updated). When a review is rejected, the synonym receives negative reinforcement (confidence reduced).

### Human Review Decisions

Approve and reject actions in the review queue now produce immutable `ReviewDecision` nodes in the graph. These are linked to the original `MatchDecision` via a `CONFIRMS` edge, creating a full audit trail from fuzzy match evaluation through human judgment.

```java
// Approve -- creates ReviewDecision, reinforces synonyms, then merges
resolver.approveReview(reviewId, "reviewer-1", "Confirmed same entity");

// Reject -- creates ReviewDecision, applies negative reinforcement
resolver.rejectReview(reviewId, "reviewer-1", "Different entities");
```

## Graph Data Model

```cypher
(:Entity {id, canonicalName, normalizedName, type, confidenceScore, status, createdAt, updatedAt})
(:Synonym {id, value, normalizedValue, source, confidence, lastConfirmedAt, supportCount, createdAt})
(:Synonym)-[:SYNONYM_OF]->(:Entity)
(:Entity {status: "MERGED"})-[:MERGED_INTO]->(:Entity {status: "ACTIVE"})
(:Entity)-[:LIBRARY_REL {type: String}]->(:Entity)

// v1.1 Decision Graph
(:MatchDecision {id, inputEntityTempId, candidateEntityId, entityType, exactScore, levenshteinScore,
                 jaroWinklerScore, jaccardScore, llmScore, graphContextScore, finalScore, outcome,
                 autoMergeThreshold, synonymThreshold, reviewThreshold, evaluator, evaluatedAt})
(:MatchDecision)-[:EVALUATED_INPUT]->(:Entity)
(:MatchDecision)-[:EVALUATED_CANDIDATE]->(:Entity)

(:ReviewDecision {id, reviewId, action, reviewerId, rationale, decidedAt})
(:ReviewDecision)-[:CONFIRMS]->(:MatchDecision)
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
