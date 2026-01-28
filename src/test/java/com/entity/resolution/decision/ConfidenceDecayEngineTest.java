package com.entity.resolution.decision;

import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.core.model.SynonymSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConfidenceDecayEngineTest {

    private ConfidenceDecayEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConfidenceDecayEngine(0.001, 0.15);
    }

    @Nested
    @DisplayName("Confidence Decay Math")
    class DecayMath {

        @Test
        @DisplayName("Fresh synonym should have full confidence")
        void freshSynonymFullConfidence() {
            Synonym synonym = createSynonym(1.0, Instant.now(), 0);
            double effective = engine.computeEffectiveConfidence(synonym, Instant.now());
            assertEquals(1.0, effective, 0.001);
        }

        @Test
        @DisplayName("Confidence should decay over time")
        void confidenceDecaysOverTime() {
            Instant createdAt = Instant.now().minus(Duration.ofDays(365));
            Synonym synonym = createSynonym(1.0, createdAt, 0);
            Instant now = Instant.now();

            double effective = engine.computeEffectiveConfidence(synonym, now);

            // exp(-0.001 * 365) ~ 0.6943
            assertTrue(effective < 1.0, "Confidence should decay");
            assertTrue(effective > 0.5, "Decay should not be too aggressive");
            assertEquals(Math.exp(-0.001 * 365), effective, 0.01);
        }

        @Test
        @DisplayName("Higher lambda should cause faster decay")
        void higherLambdaFasterDecay() {
            ConfidenceDecayEngine fastDecay = new ConfidenceDecayEngine(0.01, 0.15);
            Instant oneYearAgo = Instant.now().minus(Duration.ofDays(365));
            Synonym synonym = createSynonym(1.0, oneYearAgo, 0);
            Instant now = Instant.now();

            double slowDecayResult = engine.computeEffectiveConfidence(synonym, now);
            double fastDecayResult = fastDecay.computeEffectiveConfidence(synonym, now);

            assertTrue(fastDecayResult < slowDecayResult,
                    "Higher lambda should produce lower confidence");
        }

        @Test
        @DisplayName("Zero lambda should result in no decay")
        void zeroLambdaNoDecay() {
            ConfidenceDecayEngine noDecay = new ConfidenceDecayEngine(0.0, 0.15);
            Instant longAgo = Instant.now().minus(Duration.ofDays(3650));
            Synonym synonym = createSynonym(0.9, longAgo, 0);

            double effective = noDecay.computeEffectiveConfidence(synonym, Instant.now());
            assertEquals(0.9, effective, 0.001);
        }

        @Test
        @DisplayName("Effective confidence should be clamped to [0.0, 1.0]")
        void confidenceClampedToRange() {
            // High support count could push over 1.0
            Synonym synonym = createSynonym(0.95, Instant.now(), 10000);
            double effective = engine.computeEffectiveConfidence(synonym, Instant.now());
            assertTrue(effective <= 1.0, "Should be clamped to max 1.0");
            assertTrue(effective >= 0.0, "Should be clamped to min 0.0");
        }

        @Test
        @DisplayName("Null lastConfirmedAt should fallback to createdAt")
        void nullLastConfirmedAtFallback() {
            Synonym synonym = Synonym.builder()
                    .value("test")
                    .normalizedValue("test")
                    .source(SynonymSource.SYSTEM)
                    .confidence(0.9)
                    .build();
            // lastConfirmedAt defaults to createdAt in builder

            double effective = engine.computeEffectiveConfidence(synonym, Instant.now());
            assertTrue(effective > 0.0);
            assertTrue(effective <= 1.0);
        }
    }

    @Nested
    @DisplayName("Reinforcement Accumulation")
    class ReinforcementAccumulation {

        @Test
        @DisplayName("Support count of 0 should give zero boost")
        void zeroSupportCountZeroBoost() {
            double boost = engine.reinforcementBoost(0);
            assertEquals(0.0, boost, 0.001);
        }

        @Test
        @DisplayName("Positive support count should give positive boost")
        void positiveSupportCountPositiveBoost() {
            double boost = engine.reinforcementBoost(5);
            assertTrue(boost > 0.0, "Should have positive boost");
            assertTrue(boost <= 0.15, "Should not exceed reinforcement cap");
        }

        @Test
        @DisplayName("Boost should be logarithmic (diminishing returns)")
        void boostIsLogarithmic() {
            double boost1 = engine.reinforcementBoost(1);
            double boost10 = engine.reinforcementBoost(10);
            double boost100 = engine.reinforcementBoost(100);

            assertTrue(boost10 > boost1, "More support = more boost");
            assertTrue(boost100 > boost10, "More support = more boost");

            double delta1to10 = boost10 - boost1;
            double delta10to100 = boost100 - boost10;
            assertTrue(delta10to100 < delta1to10 * 2,
                    "Boost should have diminishing returns");
        }

        @Test
        @DisplayName("Boost should be capped at reinforcement cap")
        void boostCappedAtReinforcementCap() {
            double boost = engine.reinforcementBoost(1_000_000);
            assertEquals(0.15, boost, 0.001, "Should be capped at reinforcement cap");
        }

        @Test
        @DisplayName("Reinforcement should increase support count and update timestamp")
        void reinforceIncrementsCountAndUpdatesTimestamp() {
            Synonym synonym = createSynonym(0.8, Instant.now().minus(Duration.ofDays(30)), 3);
            Instant before = synonym.getLastConfirmedAt();

            engine.reinforce(synonym);

            assertEquals(4, synonym.getSupportCount());
            assertTrue(synonym.getLastConfirmedAt().isAfter(before) ||
                    synonym.getLastConfirmedAt().equals(before));
        }

        @Test
        @DisplayName("Reinforcement should increase effective confidence")
        void reinforcementIncreasesEffectiveConfidence() {
            Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
            Synonym withoutSupport = createSynonym(0.8, thirtyDaysAgo, 0);
            Synonym withSupport = createSynonym(0.8, thirtyDaysAgo, 10);

            Instant now = Instant.now();
            double confidenceWithout = engine.computeEffectiveConfidence(withoutSupport, now);
            double confidenceWith = engine.computeEffectiveConfidence(withSupport, now);

            assertTrue(confidenceWith > confidenceWithout,
                    "Support count should boost effective confidence");
        }
    }

    @Nested
    @DisplayName("Threshold Crossings")
    class ThresholdCrossings {

        @Test
        @DisplayName("Should trigger review when confidence decays below synonym threshold")
        void shouldTriggerReviewBelowSynonymThreshold() {
            // Create a synonym with confidence that will decay below 0.80
            Instant longAgo = Instant.now().minus(Duration.ofDays(1000));
            Synonym synonym = createSynonym(0.85, longAgo, 0);

            assertTrue(engine.shouldTriggerReview(synonym, 0.80),
                    "Should trigger review for decayed confidence");
        }

        @Test
        @DisplayName("Should not trigger review for fresh high-confidence synonym")
        void shouldNotTriggerReviewForFreshSynonym() {
            Synonym synonym = createSynonym(0.95, Instant.now(), 5);

            assertFalse(engine.shouldTriggerReview(synonym, 0.80),
                    "Should not trigger review for fresh high-confidence synonym");
        }

        @Test
        @DisplayName("Should detect stale synonym below review threshold")
        void shouldDetectStaleSynonym() {
            Instant veryOld = Instant.now().minus(Duration.ofDays(2000));
            Synonym synonym = createSynonym(0.70, veryOld, 0);

            assertTrue(engine.isStale(synonym, 0.60),
                    "Very old low-confidence synonym should be stale");
        }

        @Test
        @DisplayName("Negative reinforcement should reduce confidence")
        void negativeReinforcementReducesConfidence() {
            Synonym synonym = createSynonym(0.8, Instant.now(), 0);
            engine.negativeReinforcement(synonym, 0.05);

            assertEquals(0.75, synonym.getConfidence(), 0.001);
        }

        @Test
        @DisplayName("Negative reinforcement should not go below zero")
        void negativeReinforcementClampedAtZero() {
            Synonym synonym = createSynonym(0.03, Instant.now(), 0);
            engine.negativeReinforcement(synonym, 0.10);

            assertEquals(0.0, synonym.getConfidence(), 0.001);
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should reject negative lambda")
        void rejectNegativeLambda() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfidenceDecayEngine(-0.001, 0.15));
        }

        @Test
        @DisplayName("Should reject reinforcement cap out of range")
        void rejectInvalidReinforcementCap() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfidenceDecayEngine(0.001, -0.01));
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfidenceDecayEngine(0.001, 1.1));
        }

        @Test
        @DisplayName("Default constructor should use standard parameters")
        void defaultConstructorUsesDefaults() {
            ConfidenceDecayEngine defaultEngine = new ConfidenceDecayEngine();
            assertEquals(0.001, defaultEngine.getLambda());
            assertEquals(0.15, defaultEngine.getReinforcementCap());
        }
    }

    private Synonym createSynonym(double confidence, Instant lastConfirmedAt, long supportCount) {
        return Synonym.builder()
                .value("test synonym")
                .normalizedValue("test synonym")
                .source(SynonymSource.SYSTEM)
                .confidence(confidence)
                .lastConfirmedAt(lastConfirmedAt)
                .supportCount(supportCount)
                .build();
    }
}
