package com.entity.resolution.rules;

import com.entity.resolution.core.model.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Engine for applying normalization rules to entity names.
 * Rules are applied in priority order (lower priority number = higher precedence).
 */
public class NormalizationEngine {
    private static final Logger log = LoggerFactory.getLogger(NormalizationEngine.class);

    private final List<NormalizationRule> rules;

    public NormalizationEngine() {
        this.rules = new ArrayList<>();
    }

    public NormalizationEngine(List<NormalizationRule> rules) {
        this.rules = new ArrayList<>(rules);
        sortRules();
    }

    /**
     * Adds a rule to the engine.
     */
    public void addRule(NormalizationRule rule) {
        rules.add(rule);
        sortRules();
    }

    /**
     * Adds multiple rules to the engine.
     */
    public void addRules(List<NormalizationRule> newRules) {
        rules.addAll(newRules);
        sortRules();
    }

    /**
     * Removes a rule by name.
     */
    public boolean removeRule(String ruleName) {
        return rules.removeIf(r -> r.getName().equals(ruleName));
    }

    /**
     * Gets all rules currently in the engine.
     */
    public List<NormalizationRule> getRules() {
        return List.copyOf(rules);
    }

    /**
     * Normalizes the given name for any entity type.
     */
    public String normalize(String name) {
        return normalize(name, null);
    }

    /**
     * Normalizes the given name for a specific entity type.
     * Applies rules in priority order, filtering by entity type.
     */
    public String normalize(String name, EntityType entityType) {
        if (name == null || name.isBlank()) {
            return "";
        }

        String result = name;

        // Apply rules in priority order
        for (NormalizationRule rule : rules) {
            if (entityType == null || rule.appliesTo(entityType)) {
                String before = result;
                result = rule.apply(result);
                if (!before.equals(result)) {
                    log.debug("Rule '{}' transformed '{}' -> '{}'", rule.getName(), before, result);
                }
            }
        }

        // Final cleanup: lowercase, trim, and collapse whitespace
        result = result.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");

        return result;
    }

    /**
     * Checks if two names are equivalent after normalization.
     */
    public boolean areEquivalent(String name1, String name2, EntityType entityType) {
        String normalized1 = normalize(name1, entityType);
        String normalized2 = normalize(name2, entityType);
        return normalized1.equals(normalized2);
    }

    private void sortRules() {
        rules.sort(Comparator.comparingInt(NormalizationRule::getPriority));
    }
}
