package com.entity.resolution.similarity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default blocking key strategy using three complementary approaches:
 * <ul>
 *   <li><b>Prefix keys</b>: First 3 characters of the name (e.g., {@code pfx:mic})</li>
 *   <li><b>Sorted token keys</b>: First 2 tokens alphabetically sorted (e.g., {@code tok:corp|microsoft})</li>
 *   <li><b>Bigram keys</b>: First 2 characters (e.g., {@code bg:mi})</li>
 * </ul>
 *
 * <p>Using multiple key types increases recall (fewer missed matches) while each
 * individual key type provides selectivity (fewer false candidates).</p>
 */
public class DefaultBlockingKeyStrategy implements BlockingKeyStrategy {

    @Override
    public Set<String> generateKeys(String normalizedName) {
        Set<String> keys = new LinkedHashSet<>();
        if (normalizedName == null || normalizedName.isBlank()) {
            return keys;
        }

        String cleaned = normalizedName.toLowerCase().trim();

        // Prefix key: first 3 characters
        if (cleaned.length() >= 3) {
            keys.add("pfx:" + cleaned.substring(0, 3));
        } else if (!cleaned.isEmpty()) {
            keys.add("pfx:" + cleaned);
        }

        // Sorted token key: first 2 tokens sorted alphabetically
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length >= 2) {
            String[] sorted = Arrays.copyOf(tokens, tokens.length);
            Arrays.sort(sorted);
            keys.add("tok:" + sorted[0] + "|" + sorted[1]);
        } else if (tokens.length == 1 && !tokens[0].isEmpty()) {
            keys.add("tok:" + tokens[0]);
        }

        // Bigram key: first 2 characters
        if (cleaned.length() >= 2) {
            keys.add("bg:" + cleaned.substring(0, 2));
        } else if (!cleaned.isEmpty()) {
            keys.add("bg:" + cleaned);
        }

        return keys;
    }
}
