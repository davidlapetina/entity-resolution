package com.entity.resolution.core.model;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Opaque, immutable handle to a canonical entity.
 *
 * This reference guarantees that {@link #getId()} always returns the current
 * canonical entity ID, even if the original entity was merged into another.
 *
 * EntityReference instances can only be created by the library and should
 * only be used via library APIs to ensure referential integrity.
 */
public final class EntityReference {

    private final String originalId;
    private final EntityType type;
    private final Supplier<String> canonicalIdResolver;

    private EntityReference(String originalId, EntityType type, Supplier<String> canonicalIdResolver) {
        this.originalId = Objects.requireNonNull(originalId, "originalId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.canonicalIdResolver = Objects.requireNonNull(canonicalIdResolver, "resolver is required");
    }

    /**
     * Creates a reference that always returns the same ID (for canonical entities).
     */
    public static EntityReference of(String id, EntityType type) {
        return new EntityReference(id, type, () -> id);
    }

    /**
     * Creates a reference with lazy canonical resolution (for potentially merged entities).
     */
    public static EntityReference withResolver(String originalId, EntityType type,
                                                Supplier<String> canonicalIdResolver) {
        return new EntityReference(originalId, type, canonicalIdResolver);
    }

    /**
     * Returns the current canonical entity ID.
     * This follows the merge chain if the original entity was merged.
     */
    public String getId() {
        return canonicalIdResolver.get();
    }

    /**
     * Returns the original entity ID (may be different from canonical if merged).
     */
    public String getOriginalId() {
        return originalId;
    }

    /**
     * Returns the entity type.
     */
    public EntityType getType() {
        return type;
    }

    /**
     * Checks if this reference points to a merged (non-canonical) entity.
     */
    public boolean wasMerged() {
        return !originalId.equals(getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityReference that = (EntityReference) o;
        // Compare by canonical ID to treat merged entities as equal
        return Objects.equals(getId(), that.getId()) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), type);
    }

    @Override
    public String toString() {
        String canonicalId = getId();
        if (originalId.equals(canonicalId)) {
            return "EntityReference{id='" + canonicalId + "', type=" + type + "}";
        } else {
            return "EntityReference{originalId='" + originalId + "', canonicalId='" + canonicalId + "', type=" + type + "}";
        }
    }
}
