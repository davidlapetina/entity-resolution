package com.entity.resolution.rest.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for API key-based security.
 * Maps API keys to security roles for authentication and authorization.
 *
 * <p>In Quarkus, this is populated from {@code application.yaml}:</p>
 * <pre>
 * entity-resolution:
 *   security:
 *     enabled: true
 *     api-key-header: X-API-Key
 *     admin-keys:
 *       - "er-ak-admin-xxxx"
 *     writer-keys:
 *       - "er-ak-writer-xxxx"
 *     reader-keys:
 *       - "er-ak-reader-xxxx"
 * </pre>
 */
public class SecurityConfig {

    private final boolean enabled;
    private final String apiKeyHeader;
    private final Map<String, SecurityRole> apiKeys; // key value -> role

    private SecurityConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.apiKeyHeader = builder.apiKeyHeader;
        this.apiKeys = Collections.unmodifiableMap(new HashMap<>(builder.apiKeys));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    /**
     * Looks up the role associated with an API key.
     *
     * @param apiKey the API key value
     * @return the associated role, or null if the key is not recognized
     */
    public SecurityRole getRoleForKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return apiKeys.get(apiKey);
    }

    /**
     * Returns true if the given API key is valid.
     */
    public boolean isValidKey(String apiKey) {
        return apiKey != null && apiKeys.containsKey(apiKey);
    }

    /**
     * Returns the number of configured API keys.
     */
    public int keyCount() {
        return apiKeys.size();
    }

    /**
     * Creates a disabled security configuration.
     */
    public static SecurityConfig disabled() {
        return builder().enabled(false).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private String apiKeyHeader = "X-API-Key";
        private final Map<String, SecurityRole> apiKeys = new HashMap<>();

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder apiKeyHeader(String apiKeyHeader) {
            if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
                throw new IllegalArgumentException("apiKeyHeader must not be null or blank");
            }
            this.apiKeyHeader = apiKeyHeader;
            return this;
        }

        /**
         * Adds a single API key with the specified role.
         */
        public Builder addKey(String key, SecurityRole role) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("API key must not be null or blank");
            }
            if (role == null) {
                throw new IllegalArgumentException("Security role must not be null");
            }
            this.apiKeys.put(key, role);
            return this;
        }

        /**
         * Adds multiple API keys with the same role.
         */
        public Builder addKeys(List<String> keys, SecurityRole role) {
            if (keys == null) return this;
            for (String key : keys) {
                if (key != null && !key.isBlank()) {
                    addKey(key, role);
                }
            }
            return this;
        }

        public SecurityConfig build() {
            return new SecurityConfig(this);
        }
    }

    @Override
    public String toString() {
        return "SecurityConfig{" +
                "enabled=" + enabled +
                ", apiKeyHeader='" + apiKeyHeader + '\'' +
                ", keyCount=" + apiKeys.size() +
                '}';
    }
}
