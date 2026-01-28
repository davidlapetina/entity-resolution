package com.entity.resolution.rest.dto;

import com.entity.resolution.decision.DecisionOutcome;
import com.entity.resolution.decision.MatchDecisionRecord;

import java.time.Instant;

/**
 * REST response DTO for a match decision record.
 * Exposes all score components, thresholds, final outcome, and evaluator.
 */
public record MatchDecisionResponse(
        String id,
        String inputEntityTempId,
        String candidateEntityId,
        String entityType,
        double exactScore,
        double levenshteinScore,
        double jaroWinklerScore,
        double jaccardScore,
        Double llmScore,
        Double graphContextScore,
        double finalScore,
        DecisionOutcome outcome,
        double autoMergeThreshold,
        double synonymThreshold,
        double reviewThreshold,
        Instant evaluatedAt,
        String evaluator
) {
    /**
     * Creates a response DTO from a domain model.
     */
    public static MatchDecisionResponse from(MatchDecisionRecord record) {
        return new MatchDecisionResponse(
                record.getId(),
                record.getInputEntityTempId(),
                record.getCandidateEntityId(),
                record.getEntityType().name(),
                record.getExactScore(),
                record.getLevenshteinScore(),
                record.getJaroWinklerScore(),
                record.getJaccardScore(),
                record.getLlmScore(),
                record.getGraphContextScore(),
                record.getFinalScore(),
                record.getOutcome(),
                record.getAutoMergeThreshold(),
                record.getSynonymThreshold(),
                record.getReviewThreshold(),
                record.getEvaluatedAt(),
                record.getEvaluator()
        );
    }
}
