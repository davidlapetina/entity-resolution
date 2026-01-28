package com.entity.resolution.decision;

import com.entity.resolution.core.model.Synonym;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Computes effective confidence for synonyms using time-based decay
 * and support-count reinforcement.
 *
 * <p>Formula:</p>
 * <pre>
 * effectiveConfidence =
 *   baseConfidence * exp(-lambda * daysSinceLastConfirmed)
 *   + reinforcementBoost(supportCount)
 * </pre>
 *
 * <p>Where:</p>
 * <ul>
 *   <li>{@code lambda} is configurable (default 0.001)</li>
 *   <li>{@code reinforcementBoost} is logarithmic and capped at {@code reinforcementCap}</li>
 * </ul>
 *
 * <p>Decay is evaluated lazily at read time. Persistence of the effective
 * score only occurs when crossing thresholds.</p>
 */
public class ConfidenceDecayEngine {
    private static final Logger log = LoggerFactory.getLogger(ConfidenceDecayEngine.class);

    private final double lambda;
    private final double reinforcementCap;

    /**
     * Creates a ConfidenceDecayEngine with default parameters.
     */
    public ConfidenceDecayEngine() {
        this(0.001, 0.15);
    }

    /**
     * Creates a ConfidenceDecayEngine with configurable parameters.
     *
     * @param lambda            decay rate (higher = faster decay)
     * @param reinforcementCap  maximum boost from reinforcement (0.0-1.0)
     */
    public ConfidenceDecayEngine(double lambda, double reinforcementCap) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("lambda must be non-negative");
        }
        if (reinforcementCap < 0.0 || reinforcementCap > 1.0) {
            throw new IllegalArgumentException("reinforcementCap must be between 0.0 and 1.0");
        }
        this.lambda = lambda;
        this.reinforcementCap = reinforcementCap;
    }

    /**
     * Computes the effective confidence of a synonym at the current time.
     *
     * @param synonym the synonym to evaluate
     * @return the effective confidence (clamped to [0.0, 1.0])
     */
    public double computeEffectiveConfidence(Synonym synonym) {
        return computeEffectiveConfidence(synonym, Instant.now());
    }

    /**
     * Computes the effective confidence of a synonym at a specific point in time.
     * Visible for testing.
     *
     * @param synonym the synonym to evaluate
     * @param now     the reference time for decay calculation
     * @return the effective confidence (clamped to [0.0, 1.0])
     */
    public double computeEffectiveConfidence(Synonym synonym, Instant now) {
        double baseConfidence = synonym.getConfidence();
        Instant lastConfirmed = synonym.getLastConfirmedAt();

        if (lastConfirmed == null) {
            lastConfirmed = synonym.getCreatedAt();
        }
        if (lastConfirmed == null) {
            return baseConfidence;
        }

        double daysSinceLastConfirmed = Duration.between(lastConfirmed, now).toSeconds() / 86400.0;
        if (daysSinceLastConfirmed < 0) {
            daysSinceLastConfirmed = 0;
        }

        double decayedConfidence = baseConfidence * Math.exp(-lambda * daysSinceLastConfirmed);
        double boost = reinforcementBoost(synonym.getSupportCount());
        double effective = decayedConfidence + boost;

        // Clamp to [0.0, 1.0]
        effective = Math.max(0.0, Math.min(1.0, effective));

        log.trace("Confidence decay: base={} days={} decayed={} boost={} effective={}",
                baseConfidence, daysSinceLastConfirmed, decayedConfidence, boost, effective);

        return effective;
    }

    /**
     * Computes the reinforcement boost from support count.
     * Uses a logarithmic function capped at {@code reinforcementCap}.
     *
     * @param supportCount the number of supporting events
     * @return the boost value (0.0 to reinforcementCap)
     */
    public double reinforcementBoost(long supportCount) {
        if (supportCount <= 0) {
            return 0.0;
        }
        // log1p(x) = ln(1 + x), scaled and capped
        double raw = Math.log1p(supportCount) * 0.05;
        return Math.min(raw, reinforcementCap);
    }

    /**
     * Checks if a synonym's effective confidence has crossed below the synonym threshold.
     *
     * @param synonym          the synonym to check
     * @param synonymThreshold the threshold below which to trigger review
     * @return true if effective confidence is below synonymThreshold
     */
    public boolean shouldTriggerReview(Synonym synonym, double synonymThreshold) {
        double effective = computeEffectiveConfidence(synonym);
        return effective < synonymThreshold;
    }

    /**
     * Checks if a synonym's effective confidence has crossed below the review threshold,
     * indicating it should be marked as STALE.
     *
     * @param synonym         the synonym to check
     * @param reviewThreshold the threshold below which to mark as stale
     * @return true if effective confidence is below reviewThreshold
     */
    public boolean isStale(Synonym synonym, double reviewThreshold) {
        double effective = computeEffectiveConfidence(synonym);
        return effective < reviewThreshold;
    }

    /**
     * Reinforces a synonym by incrementing its support count
     * and updating its last confirmed timestamp.
     *
     * @param synonym the synonym to reinforce
     */
    public void reinforce(Synonym synonym) {
        synonym.reinforce();
        log.debug("Reinforced synonym '{}': supportCount={} lastConfirmedAt={}",
                synonym.getValue(), synonym.getSupportCount(), synonym.getLastConfirmedAt());
    }

    /**
     * Applies negative reinforcement by reducing the synonym's base confidence.
     * Used when a review is rejected.
     *
     * @param synonym  the synonym to weaken
     * @param penalty  the amount to reduce confidence (positive value)
     */
    public void negativeReinforcement(Synonym synonym, double penalty) {
        double newConfidence = Math.max(0.0, synonym.getConfidence() - Math.abs(penalty));
        synonym.setConfidence(newConfidence);
        log.debug("Negative reinforcement on synonym '{}': penalty={} newConfidence={}",
                synonym.getValue(), penalty, newConfidence);
    }

    public double getLambda() {
        return lambda;
    }

    public double getReinforcementCap() {
        return reinforcementCap;
    }
}
