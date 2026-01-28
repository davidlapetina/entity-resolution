package com.entity.resolution.decision;

/**
 * Action taken by a human reviewer on a review item.
 */
public enum ReviewAction {
    /** Reviewer confirmed the match - triggers merge/synonym reinforcement. */
    APPROVE,

    /** Reviewer rejected the match - triggers negative reinforcement. */
    REJECT
}
