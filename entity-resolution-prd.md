# Graph-Based Entity Resolution, Synonym Management, and Deduplication
## Product Requirements Document (PRD)

---

## 1. Overview

This document specifies a **graph-native entity resolution system** built on **FalkorDB**, designed to ensure **high data quality** through deterministic and probabilistic deduplication, explicit synonym modeling, and audit-safe merges. The system is delivered as a **reusable Java library**, with optional **LLM-assisted semantic enrichment**.

The solution targets use cases such as:
- News and economic knowledge graphs
- Company and brand intelligence
- Product Information Management (PIM)
- Document analysis and RAG preprocessing
- Graph-based analytics platforms

---

## 2. Goals and Objectives

### 2.1 Primary Goals
- Eliminate duplicate entities while preserving provenance
- Canonicalize entities around a single authoritative node
- Explicitly model synonyms and aliases as first-class graph objects
- Merge relationships automatically and safely
- Maintain explainability, confidence scoring, and auditability
- Support both deterministic logic and optional AI enrichment
- Provide a reusable, extensible Java library
- **Provide opaque entity handles that remain valid after merges**
- **Support batch ingestion with deduplication**
- **Manage relationships through the library for merge safety**

### 2.2 Non-Goals
- Full Master Data Management (MDM) replacement
- Real-time identity resolution for PII-heavy personal data
- Black-box, non-explainable AI-based merging

---

## 3. Key Concepts

- **Canonical Entity**: The authoritative node representing a real-world concept.
- **EntityReference**: An opaque handle to a canonical entity that always resolves to the current canonical ID, even after merges.
- **Synonym**: An alternative name or alias explicitly linked to a canonical entity.
- **Duplicate Entity**: A deprecated representation whose relationships have been merged.
- **Merge Ledger**: An immutable audit trail recording how and why merges occurred.
- **Confidence Score**: A numeric indicator of certainty for matches and merges.
- **Library-Managed Relationship**: A relationship created through the library API that will be auto-migrated during merges.

---

## 4. Graph Data Model

### 4.1 Node Types

#### Entity
```cypher
(:Entity {
  id: UUID,
  canonicalName: String,
  normalizedName: String,
  type: String,
  confidenceScore: Float,
  status: "ACTIVE" | "MERGED",
  createdAt: Timestamp,
  updatedAt: Timestamp
})
```

#### Synonym
```cypher
(:Synonym {
  id: UUID,
  value: String,
  normalizedValue: String,
  source: "SYSTEM" | "HUMAN" | "LLM",
  confidence: Float,
  createdAt: Timestamp
})
```

#### DuplicateEntity
```cypher
(:DuplicateEntity {
  id: UUID,
  originalName: String,
  normalizedName: String,
  sourceSystem: String,
  createdAt: Timestamp
})
```

---

### 4.2 Relationship Types

```cypher
// Synonym attachment
(:Synonym)-[:SYNONYM_OF]->(:Entity)

// Merge tracking
(:Entity {status: "MERGED"})-[:MERGED_INTO {
  confidence: Float,
  reason: String,
  mergedAt: Timestamp
}]->(:Entity {status: "ACTIVE"})

// Duplicate tracking
(:DuplicateEntity)-[:DUPLICATE_OF]->(:Entity)

// Library-managed relationships
(:Entity)-[:LIBRARY_REL {
  id: UUID,
  type: String,
  createdAt: Timestamp,
  createdBy: String
}]->(:Entity)
```

---

## 5. Name Normalization

### 5.1 Purpose
To remove non-significant tokens (e.g. "Inc.", "Ltd") and standardize input before matching.

### 5.2 Normalization Rules Table

| Pattern (Regex)       | Replacement | Entity Type | Priority |
|-----------------------|-------------|-------------|----------|
| `(?i)\bincorporated\b` | ``          | COMPANY     | 110      |
| `(?i)\binc\b\.?`      | ``          | COMPANY     | 100      |
| `(?i)\bcorporation\b` | ``          | COMPANY     | 100      |
| `(?i)\bltd\b\.?`      | ``          | COMPANY     | 100      |
| `(?i)\bcorp\b\.?`     | ``          | COMPANY     | 100      |
| `(?i)\bco\b\.?`       | ``          | COMPANY     | 90       |
| `(?i)\bsa\b\.?`       | ``          | COMPANY     | 90       |
| `(?i)\bllc\b\.?`      | ``          | COMPANY     | 90       |
| `(?i)\bplc\b\.?`      | ``          | COMPANY     | 90       |

Rules are:
- Regex-based
- Ordered by priority (higher priority applied first)
- Entity-type scoped
- Extensible via API

---

## 6. Matching Strategy

### 6.1 Deterministic Matching
- Exact match on `normalizedName` and `entityType`
- Eligible for automatic merge

### 6.2 Probabilistic Matching
Computed in Java, using:
- Levenshtein distance
- Jaro–Winkler similarity
- Token overlap (Jaccard)

Composite score:
```
score = w1 * levenshtein + w2 * jaroWinkler + w3 * tokenOverlap
```

Default weights: Levenshtein=0.4, Jaro-Winkler=0.35, Jaccard=0.25

Decision outcomes based on score thresholds:
| Score Range | Decision |
|-------------|----------|
| ≥ 0.92 | `AUTO_MERGE` |
| 0.80 - 0.92 | `SYNONYM_ONLY` |
| 0.60 - 0.80 | `REVIEW` |
| < 0.60 | `NO_MATCH` |

Optional: `LLM_ENRICH` for semantic matching

---

## 7. Optional LLM-Assisted Enrichment

### 7.1 Purpose
Resolve semantic or contextual aliases that are not string-similar.

Examples:
- "Big Blue" → IBM
- Regional or language variants
- Brand vs parent company distinctions

### 7.2 Constraints
- LLMs never perform direct merges
- Output must include confidence and reasoning
- Results are stored as synonyms or merge candidates
- Configurable confidence threshold (default ≥ 0.85)

### 7.3 Integration
Implement `LLMProvider` interface:
```java
public interface LLMProvider {
    LLMEnrichmentResponse enrich(LLMEnrichmentRequest request);
    boolean isAvailable();
}
```

---

## 8. Cypher-Level Merge Algorithm

### 8.1 Candidate Discovery
```cypher
MATCH (e:Entity)
WHERE e.type = $entityType
  AND e.normalizedName = $normalizedName
  AND e.status = 'ACTIVE'
RETURN e
LIMIT 5;
```

---

### 8.2 Synonym Attachment
```cypher
MATCH (e:Entity {id: $entityId})
CREATE (s:Synonym {
  id: $synonymId,
  value: $value,
  normalizedValue: $normalizedValue,
  source: $source,
  confidence: $confidence,
  createdAt: datetime()
})
CREATE (s)-[:SYNONYM_OF]->(e);
```

---

### 8.3 Duplicate Entity Creation
```cypher
MATCH (canonical:Entity {id: $canonicalEntityId})
CREATE (dup:DuplicateEntity {
  id: $duplicateId,
  originalName: $originalName,
  normalizedName: $normalizedName,
  sourceSystem: $sourceSystem,
  createdAt: datetime()
})
CREATE (dup)-[:DUPLICATE_OF]->(canonical);
```

---

### 8.4 Relationship Migration (Outgoing)
```cypher
MATCH (source:Entity {id: $sourceEntityId})-[r]->(target)
WHERE NOT target:Entity OR target.id <> $targetEntityId
WITH source, r, target, type(r) as relType, properties(r) as props
MATCH (newSource:Entity {id: $targetEntityId})
CREATE (newSource)-[newRel]->(target)
SET newRel = props
DELETE r;
```

---

### 8.5 Relationship Migration (Incoming)
```cypher
MATCH (source)-[r]->(target:Entity {id: $sourceEntityId})
WHERE NOT source:Entity OR source.id <> $targetEntityId
WITH source, r, target, type(r) as relType, properties(r) as props
MATCH (newTarget:Entity {id: $targetEntityId})
CREATE (source)-[newRel]->(newTarget)
SET newRel = props
DELETE r;
```

---

### 8.6 Merge Registration
```cypher
MATCH (source:Entity {id: $sourceEntityId})
MATCH (target:Entity {id: $targetEntityId})
SET source.status = 'MERGED'
SET source.updatedAt = datetime()
CREATE (source)-[:MERGED_INTO {
  confidence: $confidence,
  reason: $reason,
  mergedAt: datetime()
}]->(target);
```

---

## 9. Governance, Audit, and Data Quality

- All merges are recorded immutably in MergeLedger
- No nodes are physically deleted
- Confidence scores are preserved
- Provenance and reasoning are retained
- Enables human review and rollback analysis
- Audit actions tracked:
  - `ENTITY_CREATED`, `ENTITY_MERGED`
  - `SYNONYM_CREATED`
  - `RELATIONSHIP_CREATED`, `RELATIONSHIPS_MIGRATED`
  - `LLM_ENRICHMENT_REQUESTED`, `LLM_ENRICHMENT_COMPLETED`
  - `MANUAL_REVIEW_REQUESTED`

---

## 10. Java Library Design

### 10.1 Package Structure

```
com.entity.resolution/
├── api/                    # Public API
│   ├── EntityResolver          # Main entry point
│   ├── EntityResolutionService # Service facade
│   ├── EntityResolutionResult  # Resolution result
│   ├── ResolutionOptions       # Configuration
│   ├── BatchContext            # Batch operations
│   └── BatchResult             # Batch result
├── core/model/             # Domain models
│   ├── Entity, EntityReference, Synonym, Relationship
│   ├── DuplicateEntity, MatchResult, MergeRecord
│   └── Enums (EntityType, EntityStatus, MatchDecision, SynonymSource)
├── graph/                  # FalkorDB layer
│   ├── GraphConnection, FalkorDBConnection
│   ├── CypherExecutor
│   └── Repositories (Entity, Synonym, Relationship, Duplicate)
├── rules/                  # Normalization
│   ├── NormalizationRule, NormalizationEngine
│   └── DefaultNormalizationRules
├── similarity/             # Matching
│   ├── SimilarityAlgorithm interface
│   ├── Levenshtein, JaroWinkler, Jaccard implementations
│   └── CompositeSimilarityScorer
├── merge/                  # Merge operations
│   ├── MergeEngine, MergeResult, MergeStrategy
├── llm/                    # LLM abstraction
│   ├── LLMProvider interface
│   ├── LLMEnricher, Request/Response
│   └── NoOpLLMProvider
└── audit/                  # Audit trail
    ├── AuditService, AuditEntry, AuditAction
    └── MergeLedger
```

### 10.2 Public API Examples

#### Basic Resolution
```java
EntityResolver resolver = EntityResolver.builder()
    .falkorDB("localhost", 6379, "knowledge-graph")
    .build();

EntityResolutionResult result = resolver.resolve("Big Blue", EntityType.COMPANY);
EntityReference ref = result.getEntityReference();
```

#### With Options
```java
EntityResolutionResult result = resolver.resolve(
    "Big Blue",
    EntityType.COMPANY,
    ResolutionOptions.builder()
        .useLLM(true)
        .autoMergeThreshold(0.92)
        .build()
);
```

#### Creating Relationships
```java
EntityReference company = resolver.resolve("Acme Corp", EntityType.COMPANY).getEntityReference();
EntityReference ceo = resolver.resolve("John Smith", EntityType.PERSON).getEntityReference();

Relationship rel = resolver.createRelationship(company, ceo, "HAS_CEO");
```

#### Batch Processing
```java
try (BatchContext batch = resolver.beginBatch()) {
    EntityReference a = batch.resolve("Company A", EntityType.COMPANY).getEntityReference();
    EntityReference b = batch.resolve("Company B", EntityType.COMPANY).getEntityReference();
    batch.createRelationship(a, b, "PARTNER");
    BatchResult result = batch.commit();
}
```

#### Enhanced Result Inspection
```java
EntityResolutionResult result = resolver.resolve("Microsoft Corp", EntityType.COMPANY);

if (result.isNewEntity()) {
    // New entity was created
} else if (result.wasMatchedViaSynonym()) {
    // Matched via existing synonym
} else if (result.wasNewSynonymCreated()) {
    // Fuzzy match created new synonym
}

double confidence = result.getMatchConfidence();
String inputName = result.getInputName();
String matchedName = result.getMatchedName();
```

---

## 11. Key Usage Scenarios

### Scenario 1: Reusable Library for External Clients
- Client receives `EntityReference` (opaque handle), not raw entity IDs
- `EntityReference.getId()` always returns current canonical ID
- `EntityReference.wasMerged()` indicates if entity was merged

### Scenario 2: Client Creates Entity via API
- `result.isNewEntity()` - indicates if entity was newly created
- `result.wasMatchedViaSynonym()` - indicates synonym-based match
- `result.wasNewSynonymCreated()` - indicates new synonym was added
- `result.getInputName()` / `result.getMatchedName()` for auditing
- `resolver.findEntity()` - lookup without creation

### Scenario 3: Client Persists Additional Relationships
- All relationships created via `resolver.createRelationship()`
- Relationships use `EntityReference` (always resolves to canonical)
- Library-managed relationships auto-migrate during merges
- Query methods: `getOutgoingRelationships()`, `getIncomingRelationships()`

### Scenario 4: Batch Mode Ingestion
- `resolver.beginBatch()` starts batch context
- Deduplication within batch (same name → same entity)
- Deferred relationship creation until `commit()`
- `BatchResult` provides comprehensive summary
- `rollback()` to abandon pending changes

---

## 12. Implementation Status

### Phase 1 ✅ Complete
- Deterministic normalization and matching
- Synonym graph modeling
- Regex-based normalization rules

### Phase 2 ✅ Complete
- Automated merges with audit ledger
- MergeLedger for immutable tracking
- Relationship migration during merges

### Phase 3 ✅ Complete
- LLM abstraction layer (interface + NoOp implementation)
- Configurable confidence thresholds
- LLM enrichment integration point

### Phase 4 ✅ Complete (Architectural Enhancements)
- EntityReference for opaque entity handles
- Library-owned relationship API
- Batch processing with BatchContext
- Enhanced result flags for client decision-making

### Future Phases
- Human-in-the-loop governance workflows
- Web-based review interface
- Real-time streaming support
- Multi-graph federation

---

## 13. Final Assessment

This PRD defines a **graph-native, explainable, and extensible entity resolution system** that elevates FalkorDB into a **truth consolidation layer**. The design is:

- **Reusable**: Single JAR library with clean API
- **Auditable**: All operations tracked with provenance
- **Merge-safe**: EntityReference ensures referential integrity
- **Batch-capable**: Efficient bulk processing with deduplication
- **Extensible**: Custom normalization rules, similarity weights, and LLM providers

The library is suitable for large-scale knowledge graphs and AI-driven pipelines requiring high data quality and explainability.

---
