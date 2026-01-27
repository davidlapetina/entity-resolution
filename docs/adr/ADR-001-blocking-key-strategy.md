# ADR-001: Blocking Key Strategy

## Status
Accepted

## Context
Entity resolution requires comparing incoming entity names against all existing entities to find fuzzy matches. A naive full-scan approach is O(n) per resolution, where n is the total number of entities. At 100K+ entities, this becomes a significant bottleneck, with each fuzzy match taking tens of milliseconds.

We needed a way to narrow the candidate set before running expensive similarity algorithms (Levenshtein, Jaro-Winkler, Jaccard).

## Decision
We implemented a **blocking key strategy** using three complementary key types:

1. **Prefix keys** (`pfx:`): First 3 characters of the normalized name
2. **Sorted token keys** (`tok:`): First 2 tokens sorted alphabetically
3. **Bigram keys** (`bg:`): First 2 characters

Entities sharing at least one blocking key are considered candidates for fuzzy matching. The `BlockingKeyStrategy` interface allows custom implementations.

### Why Three Key Types?
- **Prefix**: Catches names starting with the same characters (high precision)
- **Sorted tokens**: Catches reordered names like "Corp Microsoft" vs "Microsoft Corp" (high recall)
- **Bigrams**: Provides a coarser filter as a safety net (broadest recall)

Using multiple key types increases recall (fewer missed matches) while each individual key type provides selectivity.

## Consequences

### Positive
- Fuzzy match performance improved 10-50x at scale (see `docs/PERFORMANCE.md`)
- O(k) comparisons where k << n (typically k is 0.1-5% of n)
- Pluggable via `BlockingKeyStrategy` interface for domain-specific tuning
- Blocking keys are stored as graph nodes, leveraging FalkorDB's indexing

### Negative
- Additional storage overhead (~3 blocking key nodes per entity)
- Key generation adds ~0.1ms per entity creation
- False negatives possible if entity names are radically different from all existing names (mitigated by synonym lookup as a fallback)

## Alternatives Considered
- **Soundex/Metaphone**: Phonetic algorithms work well for person names but poorly for company names
- **N-gram indexing**: Higher recall but significantly more storage and index maintenance
- **LSH (Locality-Sensitive Hashing)**: More complex to implement and tune; overkill for our typical dataset sizes
