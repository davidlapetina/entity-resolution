package com.entity.resolution.decision;

import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MatchDecisionRecordTest {

    @Test
    @DisplayName("Should build valid MatchDecisionRecord")
    void buildValidRecord() {
        MatchDecisionRecord record = MatchDecisionRecord.builder()
                .inputEntityTempId("input-1")
                .candidateEntityId("candidate-1")
                .entityType(EntityType.COMPANY)
                .exactScore(0.0)
                .levenshteinScore(0.85)
                .jaroWinklerScore(0.90)
                .jaccardScore(0.78)
                .finalScore(0.85)
                .outcome(DecisionOutcome.SYNONYM)
                .autoMergeThreshold(0.92)
                .synonymThreshold(0.80)
                .reviewThreshold(0.60)
                .evaluator("SYSTEM")
                .build();

        assertNotNull(record.getId());
        assertEquals("input-1", record.getInputEntityTempId());
        assertEquals("candidate-1", record.getCandidateEntityId());
        assertEquals(EntityType.COMPANY, record.getEntityType());
        assertEquals(0.85, record.getLevenshteinScore());
        assertEquals(0.90, record.getJaroWinklerScore());
        assertEquals(0.78, record.getJaccardScore());
        assertNull(record.getLlmScore());
        assertNull(record.getGraphContextScore());
        assertEquals(0.85, record.getFinalScore());
        assertEquals(DecisionOutcome.SYNONYM, record.getOutcome());
        assertEquals("SYSTEM", record.getEvaluator());
        assertNotNull(record.getEvaluatedAt());
    }

    @Test
    @DisplayName("Should auto-generate ID if not provided")
    void autoGenerateId() {
        MatchDecisionRecord r1 = buildMinimalRecord();
        MatchDecisionRecord r2 = buildMinimalRecord();

        assertNotNull(r1.getId());
        assertNotNull(r2.getId());
        assertNotEquals(r1.getId(), r2.getId());
    }

    @Test
    @DisplayName("Should use provided ID")
    void useProvidedId() {
        MatchDecisionRecord record = MatchDecisionRecord.builder()
                .id("custom-id")
                .inputEntityTempId("input-1")
                .candidateEntityId("candidate-1")
                .entityType(EntityType.COMPANY)
                .outcome(DecisionOutcome.NO_MATCH)
                .build();

        assertEquals("custom-id", record.getId());
    }

    @Test
    @DisplayName("Should require inputEntityTempId")
    void requireInputEntityTempId() {
        assertThrows(NullPointerException.class, () ->
                MatchDecisionRecord.builder()
                        .candidateEntityId("candidate-1")
                        .entityType(EntityType.COMPANY)
                        .outcome(DecisionOutcome.NO_MATCH)
                        .build());
    }

    @Test
    @DisplayName("Should require candidateEntityId")
    void requireCandidateEntityId() {
        assertThrows(NullPointerException.class, () ->
                MatchDecisionRecord.builder()
                        .inputEntityTempId("input-1")
                        .entityType(EntityType.COMPANY)
                        .outcome(DecisionOutcome.NO_MATCH)
                        .build());
    }

    @Test
    @DisplayName("Should require entityType")
    void requireEntityType() {
        assertThrows(NullPointerException.class, () ->
                MatchDecisionRecord.builder()
                        .inputEntityTempId("input-1")
                        .candidateEntityId("candidate-1")
                        .outcome(DecisionOutcome.NO_MATCH)
                        .build());
    }

    @Test
    @DisplayName("Should require outcome")
    void requireOutcome() {
        assertThrows(NullPointerException.class, () ->
                MatchDecisionRecord.builder()
                        .inputEntityTempId("input-1")
                        .candidateEntityId("candidate-1")
                        .entityType(EntityType.COMPANY)
                        .build());
    }

    @Test
    @DisplayName("Should handle nullable LLM and graph context scores")
    void handleNullableScores() {
        MatchDecisionRecord withLlm = MatchDecisionRecord.builder()
                .inputEntityTempId("input-1")
                .candidateEntityId("candidate-1")
                .entityType(EntityType.COMPANY)
                .outcome(DecisionOutcome.AUTO_MERGE)
                .llmScore(0.95)
                .graphContextScore(0.88)
                .build();

        assertEquals(0.95, withLlm.getLlmScore());
        assertEquals(0.88, withLlm.getGraphContextScore());
    }

    @Test
    @DisplayName("Should default evaluator to SYSTEM")
    void defaultEvaluatorToSystem() {
        MatchDecisionRecord record = buildMinimalRecord();
        assertEquals("SYSTEM", record.getEvaluator());
    }

    @Test
    @DisplayName("Should set custom evaluatedAt")
    void setCustomEvaluatedAt() {
        Instant custom = Instant.parse("2024-01-15T10:30:00Z");
        MatchDecisionRecord record = MatchDecisionRecord.builder()
                .inputEntityTempId("input-1")
                .candidateEntityId("candidate-1")
                .entityType(EntityType.COMPANY)
                .outcome(DecisionOutcome.REVIEW)
                .evaluatedAt(custom)
                .build();

        assertEquals(custom, record.getEvaluatedAt());
    }

    @Test
    @DisplayName("Equals and hashCode based on ID")
    void equalsAndHashCode() {
        MatchDecisionRecord r1 = MatchDecisionRecord.builder()
                .id("same-id")
                .inputEntityTempId("input-1")
                .candidateEntityId("candidate-1")
                .entityType(EntityType.COMPANY)
                .outcome(DecisionOutcome.NO_MATCH)
                .build();

        MatchDecisionRecord r2 = MatchDecisionRecord.builder()
                .id("same-id")
                .inputEntityTempId("input-2")
                .candidateEntityId("candidate-2")
                .entityType(EntityType.PERSON)
                .outcome(DecisionOutcome.AUTO_MERGE)
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    @DisplayName("All DecisionOutcome values should be valid")
    void allDecisionOutcomesValid() {
        DecisionOutcome[] outcomes = DecisionOutcome.values();
        assertEquals(4, outcomes.length);
        assertNotNull(DecisionOutcome.AUTO_MERGE);
        assertNotNull(DecisionOutcome.SYNONYM);
        assertNotNull(DecisionOutcome.REVIEW);
        assertNotNull(DecisionOutcome.NO_MATCH);
    }

    private MatchDecisionRecord buildMinimalRecord() {
        return MatchDecisionRecord.builder()
                .inputEntityTempId("input-1")
                .candidateEntityId("candidate-1")
                .entityType(EntityType.COMPANY)
                .outcome(DecisionOutcome.NO_MATCH)
                .build();
    }
}
