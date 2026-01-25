# Entity Resolution Library

A graph-native entity resolution library for Java, built for FalkorDB. Provides deterministic and probabilistic deduplication, explicit synonym modeling, and optional LLM-assisted semantic enrichment.

## Features

- **Entity Resolution**: Deduplicate entities using exact matching, fuzzy matching, and LLM-assisted semantic matching
- **Synonym Management**: First-class synonym modeling with confidence scores and provenance tracking
- **Merge Safety**: EntityReference handles that remain valid after merges
- **Batch Processing**: Efficient bulk ingestion with automatic deduplication
- **Audit Trail**: Immutable merge ledger for compliance and debugging
- **LLM Integration**: Optional Ollama integration for semantic entity matching (e.g., "Big Blue" → IBM)

## Requirements

- Java 21+
- FalkorDB (or Redis with RedisGraph module)
- Ollama (optional, for LLM-assisted matching)

## Installation

### Maven

```xml
<dependency>
    <groupId>com.entity.resolution</groupId>
    <artifactId>entity-resolution</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.entity.resolution:entity-resolution:1.0.0'
```

## Quick Start

### Basic Entity Resolution

```java
import com.entity.resolution.api.*;
import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.*;

// Connect to FalkorDB
GraphConnection connection = new FalkorDBConnection("localhost", 6379, "knowledge-graph");

// Create resolver
EntityResolver resolver = EntityResolver.builder()
    .graphConnection(connection)
    .build();

// Resolve an entity
EntityResolutionResult result = resolver.resolve("Microsoft Corp", EntityType.COMPANY);
EntityReference ref = result.getEntityReference();

// The reference always points to the canonical entity
String canonicalId = ref.getId();
```

### With Fuzzy Matching Options

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
    System.out.println("Created synonym: " + result.getInputName() +
                       " -> " + result.getMatchedName());
}
```

### Creating Relationships

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
    // Automatic deduplication within batch
    EntityReference a = batch.resolve("Company A", EntityType.COMPANY).getEntityReference();
    EntityReference b = batch.resolve("Company B", EntityType.COMPANY).getEntityReference();
    EntityReference aDup = batch.resolve("COMPANY A", EntityType.COMPANY).getEntityReference();

    // a and aDup point to the same entity
    assert a.getId().equals(aDup.getId());

    batch.createRelationship(a, b, "PARTNER");

    BatchResult result = batch.commit();
    System.out.println("Created " + result.newEntitiesCreated() + " entities");
}
```

### LLM-Assisted Matching (Ollama)

```java
import com.entity.resolution.llm.*;

// Create Ollama provider (requires Ollama running locally)
OllamaLLMProvider llmProvider = OllamaLLMProvider.builder()
    .baseUrl("http://localhost:11434")
    .model("llama3.2")
    .build();

// Create resolver with LLM support
EntityResolver resolver = EntityResolver.builder()
    .graphConnection(connection)
    .llmProvider(llmProvider)
    .options(ResolutionOptions.withLLM())
    .build();

// Now semantic matching works
// "Big Blue" will match "IBM"
EntityResolutionResult result = resolver.resolve("Big Blue", EntityType.COMPANY);
```

## Core Concepts

### EntityReference

An opaque handle to a canonical entity that always resolves to the current canonical ID, even after merges:

```java
EntityReference ref = result.getEntityReference();
String id = ref.getId();           // Always returns current canonical ID
String originalId = ref.getOriginalId();  // Returns ID at creation time
boolean merged = ref.wasMerged();  // True if entity was merged
```

### Resolution Results

```java
EntityResolutionResult result = resolver.resolve("Microsoft", EntityType.COMPANY);

result.isNewEntity();           // True if entity was newly created
result.wasMatchedViaSynonym();  // True if matched via existing synonym
result.wasNewSynonymCreated();  // True if fuzzy match created new synonym
result.getMatchConfidence();    // Confidence score (0.0 - 1.0)
result.getInputName();          // Original input name
result.getMatchedName();        // Canonical name matched to
```

### Match Decisions

| Score Range | Decision | Action |
|-------------|----------|--------|
| >= 0.92 | AUTO_MERGE | Automatically merge entities |
| 0.80 - 0.92 | SYNONYM_ONLY | Create synonym link |
| 0.60 - 0.80 | REVIEW | Flag for manual review |
| < 0.60 | NO_MATCH | Create new entity |

## Configuration

### Resolution Options

```java
ResolutionOptions options = ResolutionOptions.builder()
    .autoMergeThreshold(0.92)      // Threshold for automatic merges
    .synonymThreshold(0.80)        // Threshold for synonym creation
    .reviewThreshold(0.60)         // Threshold for review flagging
    .useLLM(true)                  // Enable LLM-assisted matching
    .llmConfidenceThreshold(0.85)  // Minimum LLM confidence
    .build();
```

### Similarity Weights

The composite similarity score uses three algorithms:

```
score = 0.4 * levenshtein + 0.35 * jaroWinkler + 0.25 * jaccard
```

### Normalization Rules

Built-in rules remove common suffixes (Inc, Ltd, Corp, etc.):

| Pattern | Entity Type |
|---------|-------------|
| `\bincorporated\b` | COMPANY |
| `\binc\b\.?` | COMPANY |
| `\bcorporation\b` | COMPANY |
| `\bltd\b\.?` | COMPANY |
| `\bcorp\b\.?` | COMPANY |
| `\bco\b\.?` | COMPANY |
| `\bllc\b\.?` | COMPANY |

## Ollama Setup

For LLM-assisted matching, install Ollama:

```bash
# Install Ollama (macOS)
brew install ollama

# Start Ollama
ollama serve

# Pull the model
ollama pull llama3.2
```

## Graph Data Model

### Node Types

```cypher
// Canonical entity
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

// Synonym
(:Synonym {
  id: UUID,
  value: String,
  normalizedValue: String,
  source: "SYSTEM" | "HUMAN" | "LLM",
  confidence: Float,
  createdAt: Timestamp
})
```

### Relationship Types

```cypher
(:Synonym)-[:SYNONYM_OF]->(:Entity)
(:Entity {status: "MERGED"})-[:MERGED_INTO]->(:Entity {status: "ACTIVE"})
(:Entity)-[:LIBRARY_REL {type: String}]->(:Entity)
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/entity-resolution.git
cd entity-resolution

# Build
mvn clean install

# Run tests
mvn test

# Run tests with Ollama integration tests
# (requires Ollama running locally with llama3.2)
mvn test -Dtest=OllamaLLMProviderTest
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Acknowledgments

- Built for [FalkorDB](https://www.falkordb.com/)
- LLM integration powered by [Ollama](https://ollama.ai/)
