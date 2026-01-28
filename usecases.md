# Use Cases and Target Audience

## Who Is This For?

This project is designed for teams that require **stable, explainable, and auditable entity identity** across complex systems. It is particularly well-suited for the following audiences:

### LLM & AI Platform Teams
- Building **LLM-powered applications** that must reason about real-world entities
- Operating **MCP servers, agent frameworks, or tool-based LLM systems**
- Implementing **RAG pipelines** where entity identity consistency matters
- Needing **long-term memory** for agents without allowing the LLM to mutate truth

### Data Platform & Integration Teams
- Ingesting data from **multiple heterogeneous sources**
- Dealing with **duplicate, drifting, or ambiguous entity identifiers**
- Building **data lakes, data meshes, or integration hubs**
- Wanting deterministic entity resolution without opaque ML-only systems

### Data Governance & Metadata Owners
- Maintaining **data catalogs, metadata registries, or lineage systems**
- Resolving datasets, domains, products, or organizations across tools
- Requiring **auditability, provenance, and human-in-the-loop control**

### Regulated & Enterprise Environments
- Finance, healthcare, public sector, or any audit-sensitive domain
- Teams that must explain *why* two records were treated as the same entity
- Organizations that cannot delegate final authority to automated systems

---

## LLM-Centric Use Cases

### MCP Server: Entity Resolution as a Tool

**Scenario**  
You are building an MCP (Model Context Protocol) server that exposes tools to LLM agents.

**How it works**
- The system exposes tools such as:
    - `resolve_entity(name)`
    - `get_entity_context(entity_id)`
    - `get_entity_decisions(entity_id)`
- The LLM:
    1. Encounters an ambiguous reference (e.g. “Big Blue”)
    2. Calls `resolve_entity("Big Blue")`
    3. Receives a stable, canonical entity ID
    4. Uses the returned entity graph as grounded context

**Why this matters**
- Prevents entity hallucination
- Enables multi-step reasoning with consistent references
- Keeps entity authority outside the LLM

**Key property**
- The LLM can query identity, but cannot create, merge, or mutate it

**How to: Java**

```java
// Build MCP tool definitions (all read-only)
EntityResolver resolver = EntityResolver.builder()
    .falkorDB("localhost", 6379, "knowledge-graph")
    .build();

EntityResolutionMcpTools mcpTools = new EntityResolutionMcpTools(resolver);
List<McpToolDefinition> tools = mcpTools.getToolDefinitions();
// Returns 5 tools: resolve_entity, get_entity_context, get_entity_decisions,
//                   search_entities, get_entity_synonyms

// Execute a tool (e.g., from an MCP server handler)
McpToolDefinition resolveTool = mcpTools.getTool("resolve_entity").orElseThrow();
Map<String, Object> result = resolveTool.handler().apply(
    Map.of("name", "Big Blue", "entity_type", "COMPANY"));
// Returns: {found: true, entityId: "...", canonicalName: "IBM", type: "COMPANY", ...}
```

**How to: REST**

```bash
# No separate MCP endpoint required -- use the standard REST API.
# The MCP server wraps these calls.
curl -H "X-API-Key: $KEY" \
  http://localhost:8080/api/v1/entities/resolve \
  -d '{"name": "Big Blue", "entityType": "COMPANY"}'
```

---

### Entity-Grounded RAG (Retrieval-Augmented Generation)

**Scenario**  
You are building a RAG system for analytical or factual queries.

**Typical flow**
1. User asks: “What were Big Blue’s Q3 earnings?”
2. LLM resolves the entity reference via the resolver
3. Canonical entity (e.g. IBM) is identified
4. The system retrieves:
    - Entity-linked documents
    - Structured relationships
    - Historical aliases and context
5. The LLM answers using entity-grounded context

**What this avoids**
- Entity drift across retrievals
- Confusion between similarly named organizations
- Silent changes in meaning over time

**Key advantage**
- Retrieval is anchored to entity identity, not just text similarity

**How to: Java**

```java
// 1. Resolve entity reference
EntityResolutionResult result = resolver.resolve("Big Blue", EntityType.COMPANY);
EntityReference ref = result.getEntityReference();

// 2. Export the entity subgraph for RAG context
Optional<EntitySubgraph> subgraph = resolver.exportEntitySubgraph(ref.getId(), 2);
EntitySubgraph sg = subgraph.orElseThrow();
// sg.rootEntity()      -- the canonical entity (IBM)
// sg.synonyms()        -- ["Big Blue", "International Business Machines", ...]
// sg.relationships()   -- connected entities at depth <= 2
// sg.relatedEntities() -- entities reachable within 2 hops
// sg.decisions()       -- match decision history

// 3. Link documents to the entity
resolver.linkDocument(ref, "doc-q3-2024", "IBM Q3 2024 Earnings Report",
    Map.of("source", "SEC", "year", "2024"));

// 4. Retrieve linked documents
List<Relationship> docs = resolver.getLinkedDocuments(ref);
```

**How to: REST**

```bash
# Export subgraph (depth 1-3)
curl -H "X-API-Key: $KEY" \
  http://localhost:8080/api/v1/entities/{id}/subgraph?depth=2

# Get full entity context (synonyms + relationships + decisions + merge history)
curl -H "X-API-Key: $KEY" \
  http://localhost:8080/api/v1/entities/{id}/context
```

---

### Long-Term Memory for LLM Agents

**Scenario**  
You are building autonomous or semi-autonomous agents with persistent memory.

**Problem**
- Agents forget that multiple names refer to the same entity
- Past assumptions and corrections are lost or overwritten
- No audit trail of how identity decisions evolved

**Solution**
- The entity graph acts as long-term semantic memory
- MatchDecision nodes store automated reasoning signals
- ReviewDecision nodes store human corrections and approvals

**Outcome**
- Identity knowledge persists across sessions
- Human feedback becomes durable, inspectable memory
- Agent behavior improves without hidden state

**How to: Java**

```java
// Agents use findEntity (read-only) to check identity without creating entities
Optional<EntityReference> ref = resolver.findEntity("Big Blue", EntityType.COMPANY);

// Get full context including synonyms, decisions, and merge history
Optional<EntityContext> ctx = resolver.getEntityContext(entityId);
EntityContext context = ctx.orElseThrow();
context.synonyms();     // all known aliases
context.decisions();    // scoring history (explainable AI)
context.mergeHistory(); // identity evolution over time

// Query match decisions for explainability
List<MatchDecisionRecord> decisions = resolver.getDecisionsForEntity(entityId);
for (MatchDecisionRecord d : decisions) {
    // d.getFinalScore(), d.getOutcome(), d.getEvaluator()
}
```

---

## Data Governance & Metadata Governance Use Cases

### Identity Backbone for Data Catalogs

**Scenario**  
Integrating with data catalog tools such as:
- DataHub
- OpenMetadata
- Amundsen
- Collibra

**Problem**
- The same dataset appears under different names
- Ownership, lineage, and documentation fragment
- Trust in the catalog degrades

**How this system helps**
- Resolves datasets, tables, schemas, and domains as entities
- Maintains synonym graphs for technical and business names
- Applies confidence decay to surface stale or unreviewed metadata

**Result**
- A single, canonical identity per dataset
- Clear provenance of naming and resolution decisions

**How to: Java**

```java
// Use metadata entity types: DATASET, TABLE, SCHEMA, DOMAIN
EntityResolver resolver = EntityResolver.builder()
    .falkorDB("localhost", 6379, "data-catalog")
    .normalizationEngine(MetadataNormalizationRules.createFullEngine())
    .build();

// These all resolve to the same canonical entity:
resolver.resolve("sales_data_v1", EntityType.DATASET);
resolver.resolve("sales_data_v2", EntityType.DATASET);
resolver.resolve("sales_data_final", EntityType.DATASET);
resolver.resolve("sales_data_2024", EntityType.DATASET);
// Normalized form: "sales data" -- version/date/status suffixes are stripped

// Schema resolution strips common prefixes
resolver.resolve("dbo.customers", EntityType.SCHEMA);
resolver.resolve("public.customers", EntityType.SCHEMA);
// Both normalize to "customers"

// Link catalog documentation
EntityReference dataset = resolver.resolve("sales_data", EntityType.DATASET).getEntityReference();
resolver.linkDocument(dataset, "datahub://sales_data", "DataHub: Sales Data",
    Map.of("catalog", "DataHub", "domain", "Sales"));
```

---

### Metadata Reconciliation Across Systems

**Scenario**
Reconciling entities across:
- ETL pipelines
- BI tools
- Documentation
- Monitoring systems

**Capabilities**
- Track that “sales_2024”, “sales_fy24”, and “sales-report-final” refer to the same entity
- Preserve historical naming without losing canonical identity
- Allow human validation when ambiguity increases

**Benefit**
- Metadata remains coherent as systems evolve

**How to: Java**

```java
// Service/API entity types with environment suffix normalization
resolver.resolve("payment-service-dev", EntityType.SERVICE);
resolver.resolve("payment-service-prod", EntityType.SERVICE);
resolver.resolve("payment-service-staging", EntityType.SERVICE);
// All normalize to "payment service"

// Batch import from multiple systems
try (BatchContext batch = resolver.beginBatch()) {
    // From ETL pipeline
    batch.resolve("sales_2024", EntityType.DATASET);
    // From BI tool
    batch.resolve("sales_fy24", EntityType.DATASET);
    // From documentation
    batch.resolve("sales-report-final", EntityType.DATASET);

    BatchResult result = batch.commit();
    // result.totalEntitiesResolved() -- shows deduplication
}
```

---

### Regulatory Lineage and Auditability

**Scenario**
A regulator or auditor asks:
“Why were these two records treated as the same entity?”

**What the system provides**
- Full decision lineage:
    - MatchDecision scores and thresholds
    - ReviewDecision approvals or rejections
    - Merge history with timestamps
- Immutable, inspectable graph of identity evolution

**Why this matters**
- Entity resolution becomes defensible, not just probabilistic
- Compliance requirements are met without manual reconstruction

**How to: Java**

```java
// Get full entity context for audit
Optional<EntityContext> ctx = resolver.getEntityContext(entityId);
EntityContext context = ctx.orElseThrow();

// Examine match decision history
for (MatchDecisionRecord d : context.decisions()) {
    System.out.printf("Decision %s: score=%.3f, outcome=%s, evaluator=%s%n",
        d.getId(), d.getFinalScore(), d.getOutcome(), d.getEvaluator());
    // Individual scores: d.getLevenshteinScore(), d.getJaroWinklerScore(), etc.
    // Thresholds at time of decision: d.getAutoMergeThreshold(), d.getSynonymThreshold()
}

// Examine merge history
for (MergeRecord m : context.mergeHistory()) {
    System.out.printf("Merged %s -> %s at %s (confidence=%.3f, decision=%s)%n",
        m.sourceEntityName(), m.targetEntityName(), m.timestamp(),
        m.confidenceScore(), m.decision());
}

// Get audit entries for full provenance
List<AuditEntry> audit = resolver.getService().getAuditService().getEntriesForEntity(entityId);
```

**How to: REST**

```bash
# Full context with all audit data in one call
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/entities/{id}/context

# Match decisions only
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/entities/{id}/decisions

# Merge history only
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/entities/{id}/merge-history

# Full audit trail
curl -H "X-API-Key: $KEY" http://localhost:8080/api/v1/entities/{id}/audit
```

---

### Lightweight Master Data Management (MDM)

**Scenario**
You need MDM-like capabilities without the cost and rigidity of traditional MDM platforms.

**Characteristics**
- Developer-first and API-driven
- No monolithic workflows
- Human-in-the-loop by design
- Explainable rather than opaque

**Positioning**
- A lightweight alternative to traditional MDM systems
- Focused on identity, provenance, and confidence
- Suitable for modern, distributed architectures

**How to: Java**

```java
// Configure as a library with review queue for human-in-the-loop
EntityResolver resolver = EntityResolver.builder()
    .falkorDB("localhost", 6379, "mdm-graph")
    .options(ResolutionOptions.builder()
        .autoMergeThreshold(0.95)  // High threshold for auto-merge
        .synonymThreshold(0.85)
        .reviewThreshold(0.70)    // Route ambiguous cases to review
        .build())
    .build();

// Programmatic batch ingestion
try (BatchContext batch = resolver.beginBatch()) {
    for (Record record : sourceSystem.getRecords()) {
        EntityResolutionResult result = batch.resolve(record.name(), record.type());
        if (result.isNewEntity()) {
            // Track new master records
        }
    }
    batch.commit();
}

// Review queue for human decisions
ReviewService reviewService = resolver.getService().getReviewService();
Page<ReviewItem> pending = reviewService.getPendingReviews(PageRequest.of(0, 50));
for (ReviewItem item : pending.getContent()) {
    // Present to human reviewer, then:
    // reviewService.approve(item.getId(), "reviewer-id", "Confirmed same entity");
    // -- or --
    // reviewService.reject(item.getId(), "reviewer-id", "Different entities");
}
```

---

## Why This Is Safe for AI Systems

- LLMs are treated as signal generators, not authorities
- Automated matches are never final by default
- Human review is explicit, versioned, and auditable
- All identity decisions are immutable and inspectable

This makes the system suitable as a foundational component in AI-assisted but human-governed architectures.
