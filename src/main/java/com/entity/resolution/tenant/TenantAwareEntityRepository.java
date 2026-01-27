package com.entity.resolution.tenant;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.graph.CypherExecutor;
import com.entity.resolution.graph.EntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tenant-aware entity repository that automatically filters queries by the
 * current tenant from {@link TenantContext}.
 *
 * <p>When a tenant is set in the context, all queries are scoped to that tenant.
 * When no tenant is set, queries run without tenant filtering (backward compatible).</p>
 *
 * <p>Entities created through this repository are automatically tagged with
 * the current tenant ID.</p>
 */
public class TenantAwareEntityRepository extends EntityRepository {
    private static final Logger log = LoggerFactory.getLogger(TenantAwareEntityRepository.class);

    private final CypherExecutor executor;

    public TenantAwareEntityRepository(CypherExecutor executor) {
        super(executor);
        this.executor = executor;
    }

    @Override
    public Entity save(Entity entity) {
        Entity saved = super.save(entity);
        setTenantOnEntity(saved.getId());
        return saved;
    }

    @Override
    public Entity save(Entity entity, Set<String> blockingKeys) {
        Entity saved = super.save(entity, blockingKeys);
        setTenantOnEntity(saved.getId());
        return saved;
    }

    @Override
    public List<Entity> findByNormalizedName(String normalizedName, EntityType entityType) {
        String tenantId = TenantContext.getTenant();
        if (tenantId == null) {
            return super.findByNormalizedName(normalizedName, entityType);
        }

        List<Map<String, Object>> results = executor.getConnection().query("""
                MATCH (e:Entity)
                WHERE e.normalizedName = $normalizedName
                  AND e.type = $entityType
                  AND e.status = 'ACTIVE'
                  AND e.tenantId = $tenantId
                RETURN e.id as id, e.canonicalName as canonicalName,
                       e.normalizedName as normalizedName, e.type as type,
                       e.confidenceScore as confidenceScore
                """, Map.of(
                "normalizedName", normalizedName,
                "entityType", entityType.name(),
                "tenantId", tenantId
        ));
        return results.stream().map(EntityRepository::mapToEntity).toList();
    }

    @Override
    public List<Entity> findAllActive(EntityType entityType) {
        String tenantId = TenantContext.getTenant();
        if (tenantId == null) {
            return super.findAllActive(entityType);
        }

        List<Map<String, Object>> results = executor.getConnection().query("""
                MATCH (e:Entity)
                WHERE e.type = $entityType
                  AND e.status = 'ACTIVE'
                  AND e.tenantId = $tenantId
                RETURN e.id as id, e.canonicalName as canonicalName,
                       e.normalizedName as normalizedName, e.type as type,
                       e.confidenceScore as confidenceScore
                """, Map.of(
                "entityType", entityType.name(),
                "tenantId", tenantId
        ));
        return results.stream().map(EntityRepository::mapToEntity).toList();
    }

    @Override
    public List<Entity> findCandidatesByBlockingKeys(Set<String> blockingKeys, EntityType entityType) {
        String tenantId = TenantContext.getTenant();
        if (tenantId == null) {
            return super.findCandidatesByBlockingKeys(blockingKeys, entityType);
        }

        if (blockingKeys.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> results = executor.getConnection().query("""
                MATCH (e:Entity)-[:HAS_BLOCKING_KEY]->(bk:BlockingKey)
                WHERE bk.value IN $keys
                  AND e.type = $entityType
                  AND e.status = 'ACTIVE'
                  AND e.tenantId = $tenantId
                RETURN DISTINCT e.id as id, e.canonicalName as canonicalName,
                       e.normalizedName as normalizedName, e.type as type,
                       e.confidenceScore as confidenceScore
                """, Map.of(
                "keys", new java.util.ArrayList<>(blockingKeys),
                "entityType", entityType.name(),
                "tenantId", tenantId
        ));
        return results.stream().map(EntityRepository::mapToEntity).toList();
    }

    @Override
    public Optional<Entity> findById(String entityId) {
        String tenantId = TenantContext.getTenant();
        if (tenantId == null) {
            return super.findById(entityId);
        }

        List<Map<String, Object>> results = executor.getConnection().query("""
                MATCH (e:Entity {id: $id})
                WHERE e.tenantId = $tenantId
                RETURN e.id as id, e.canonicalName as canonicalName,
                       e.normalizedName as normalizedName, e.type as type,
                       e.confidenceScore as confidenceScore, e.status as status,
                       e.createdAt as createdAt, e.updatedAt as updatedAt
                """, Map.of("id", entityId, "tenantId", tenantId));

        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(EntityRepository.mapToEntity(results.get(0)));
    }

    private void setTenantOnEntity(String entityId) {
        String tenantId = TenantContext.getTenant();
        if (tenantId != null) {
            executor.getConnection().execute("""
                    MATCH (e:Entity {id: $id})
                    SET e.tenantId = $tenantId
                    """, Map.of("id", entityId, "tenantId", tenantId));
            log.debug("Set tenantId={} on entity {}", tenantId, entityId);
        }
    }
}
