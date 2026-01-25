package com.entity.resolution.rules;

import com.entity.resolution.core.model.EntityType;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A rule for normalizing entity names using regex pattern matching.
 * Rules have priority ordering and can be scoped to specific entity types.
 */
public class NormalizationRule {
    private final String name;
    private final Pattern pattern;
    private final String replacement;
    private final Set<EntityType> applicableTypes;
    private final int priority;

    private NormalizationRule(Builder builder) {
        this.name = builder.name;
        this.pattern = Pattern.compile(builder.pattern, Pattern.CASE_INSENSITIVE);
        this.replacement = builder.replacement;
        this.applicableTypes = builder.applicableTypes != null ?
                Set.copyOf(builder.applicableTypes) : Set.of();
        this.priority = builder.priority;
    }

    public String getName() {
        return name;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public Set<EntityType> getApplicableTypes() {
        return applicableTypes;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Checks if this rule applies to the given entity type.
     * If no types are specified, the rule applies to all types.
     */
    public boolean appliesTo(EntityType type) {
        return applicableTypes.isEmpty() || applicableTypes.contains(type);
    }

    /**
     * Applies this rule to the given input string.
     */
    public String apply(String input) {
        if (input == null) {
            return null;
        }
        return pattern.matcher(input).replaceAll(replacement);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NormalizationRule that = (NormalizationRule) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "NormalizationRule{" +
                "name='" + name + '\'' +
                ", pattern=" + pattern.pattern() +
                ", priority=" + priority +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String pattern;
        private String replacement;
        private Set<EntityType> applicableTypes;
        private int priority = 100;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder replacement(String replacement) {
            this.replacement = replacement;
            return this;
        }

        public Builder applicableTypes(Set<EntityType> applicableTypes) {
            this.applicableTypes = applicableTypes;
            return this;
        }

        public Builder applicableTypes(EntityType... types) {
            this.applicableTypes = Set.of(types);
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public NormalizationRule build() {
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(pattern, "pattern is required");
            Objects.requireNonNull(replacement, "replacement is required");
            return new NormalizationRule(this);
        }
    }
}
