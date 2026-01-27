# ADR-002: Compensating Transactions for Merge Operations

## Status
Accepted

## Context
Entity merges are multi-step operations involving:
1. Marking the source entity as MERGED
2. Migrating synonyms from source to target
3. Migrating relationships from source to target
4. Creating a duplicate record
5. Recording audit entries
6. Updating the merge ledger

FalkorDB does not support multi-statement transactions with rollback. If a failure occurs mid-merge (e.g., network error during step 3), the graph could be left in an inconsistent state with partially migrated relationships.

## Decision
We implemented a **compensating transaction pattern** in `MergeEngine.merge()`:

1. Before executing the merge, the engine captures the pre-merge state of both entities
2. Each step is executed sequentially with error handling
3. If any step fails, a compensation routine reverses previously completed steps:
   - Re-activate the source entity (set status back to ACTIVE)
   - Reverse any migrated relationships
   - Remove created duplicate records
4. The compensation is best-effort; any compensation failures are logged but do not throw

### Merge Steps Order
The steps are ordered to minimize the impact of partial failures:
1. **Validate** (read-only, safe to fail)
2. **Record merge in ledger** (append-only, idempotent)
3. **Migrate synonyms** (additive, can be reversed)
4. **Migrate relationships** (most complex, highest risk)
5. **Create duplicate record** (additive)
6. **Mark source as MERGED** (final step, point of no return)
7. **Audit** (post-merge recording)

## Consequences

### Positive
- Graph remains consistent even under failures
- No dependency on database-level transaction support
- Audit trail captures both the attempted merge and any compensation
- MergeListeners are only notified after successful completion

### Negative
- Compensation is best-effort, not guaranteed (defense-in-depth with idempotent operations)
- Small window where concurrent reads may see partially merged state
- Additional complexity in merge logic

## Alternatives Considered
- **Saga pattern with event log**: More robust but requires event infrastructure we don't have
- **Two-phase commit**: Requires database support not available in FalkorDB
- **Optimistic locking with retry**: Simpler but doesn't handle mid-operation failures
