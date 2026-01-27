package com.entity.resolution.retention;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for data retention policies.
 * Defines how long various types of data should be retained before cleanup.
 *
 * @param mergedEntityRetention how long to keep MERGED entities
 * @param auditEntryRetention   how long to keep audit entries
 * @param reviewItemRetention   how long to keep completed review items
 * @param softDeleteEnabled     whether to use soft delete (set deletedAt) vs hard delete
 */
public record RetentionPolicy(
        Duration mergedEntityRetention,
        Duration auditEntryRetention,
        Duration reviewItemRetention,
        boolean softDeleteEnabled
) {
    public RetentionPolicy {
        Objects.requireNonNull(mergedEntityRetention, "mergedEntityRetention is required");
        Objects.requireNonNull(auditEntryRetention, "auditEntryRetention is required");
        Objects.requireNonNull(reviewItemRetention, "reviewItemRetention is required");
        if (mergedEntityRetention.isNegative()) {
            throw new IllegalArgumentException("mergedEntityRetention must not be negative");
        }
        if (auditEntryRetention.isNegative()) {
            throw new IllegalArgumentException("auditEntryRetention must not be negative");
        }
        if (reviewItemRetention.isNegative()) {
            throw new IllegalArgumentException("reviewItemRetention must not be negative");
        }
    }

    /**
     * Creates a default retention policy.
     * - Merged entities: 1 year
     * - Audit entries: 7 years (compliance)
     * - Review items: 90 days
     * - Soft delete enabled
     */
    public static RetentionPolicy defaults() {
        return new RetentionPolicy(
                Duration.ofDays(365),
                Duration.ofDays(2555),
                Duration.ofDays(90),
                true
        );
    }

    /**
     * Creates a retention policy with no expiration (infinite retention).
     */
    public static RetentionPolicy noExpiration() {
        return new RetentionPolicy(
                Duration.ofDays(36500),
                Duration.ofDays(36500),
                Duration.ofDays(36500),
                true
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration mergedEntityRetention = Duration.ofDays(365);
        private Duration auditEntryRetention = Duration.ofDays(2555);
        private Duration reviewItemRetention = Duration.ofDays(90);
        private boolean softDeleteEnabled = true;

        public Builder mergedEntityRetention(Duration duration) {
            this.mergedEntityRetention = duration;
            return this;
        }

        public Builder auditEntryRetention(Duration duration) {
            this.auditEntryRetention = duration;
            return this;
        }

        public Builder reviewItemRetention(Duration duration) {
            this.reviewItemRetention = duration;
            return this;
        }

        public Builder softDeleteEnabled(boolean enabled) {
            this.softDeleteEnabled = enabled;
            return this;
        }

        public RetentionPolicy build() {
            return new RetentionPolicy(mergedEntityRetention, auditEntryRetention,
                    reviewItemRetention, softDeleteEnabled);
        }
    }
}
