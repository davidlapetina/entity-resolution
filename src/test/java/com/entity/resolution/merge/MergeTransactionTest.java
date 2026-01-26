package com.entity.resolution.merge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MergeTransaction compensating transaction pattern.
 */
class MergeTransactionTest {

    @Test
    void successfulTransaction_noCompensationsRun() {
        List<String> log = new ArrayList<>();

        try (MergeTransaction tx = new MergeTransaction()) {
            tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
            tx.execute("step2", () -> log.add("op2"), () -> log.add("comp2"));
            tx.markSuccess();
        }

        assertEquals(List.of("op1", "op2"), log);
    }

    @Test
    void failedOperation_runsCompensationsInReverse() {
        List<String> log = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> {
            try (MergeTransaction tx = new MergeTransaction()) {
                tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
                tx.execute("step2", () -> log.add("op2"), () -> log.add("comp2"));
                tx.execute("step3", () -> {
                    throw new RuntimeException("step3 failed");
                }, () -> log.add("comp3"));
            }
        });

        // Operations 1 and 2 ran, then compensations 2 and 1 in reverse order
        assertEquals(List.of("op1", "op2", "comp2", "comp1"), log);
    }

    @Test
    void closedWithoutSuccess_runsAllCompensations() {
        List<String> log = new ArrayList<>();

        MergeTransaction tx = new MergeTransaction();
        tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
        tx.execute("step2", () -> log.add("op2"), () -> log.add("comp2"));
        // Don't call markSuccess()
        tx.close();

        assertEquals(List.of("op1", "op2", "comp2", "comp1"), log);
    }

    @Test
    void executeNoCompensation_doesNotAddToStack() {
        List<String> log = new ArrayList<>();

        try (MergeTransaction tx = new MergeTransaction()) {
            tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
            tx.executeNoCompensation("step2", () -> log.add("op2"));
            // Don't call markSuccess()
        }

        // Only comp1 should run (step2 has no compensation)
        assertEquals(List.of("op1", "op2", "comp1"), log);
    }

    @Test
    void compensationFailure_continuesRemainingCompensations() {
        List<String> log = new ArrayList<>();

        MergeTransaction tx = new MergeTransaction();
        tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
        tx.execute("step2", () -> log.add("op2"), () -> {
            throw new RuntimeException("compensation failed");
        });
        tx.execute("step3", () -> log.add("op3"), () -> log.add("comp3"));
        // Don't call markSuccess()
        tx.close();

        // All ops ran, comp3 ran, comp2 failed but comp1 still ran
        assertEquals(List.of("op1", "op2", "op3", "comp3", "comp1"), log);
    }

    @Test
    void markSuccess_preventsCompensationsOnClose() {
        List<String> log = new ArrayList<>();

        try (MergeTransaction tx = new MergeTransaction()) {
            tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
            tx.markSuccess();
        }

        assertEquals(List.of("op1"), log);
        // comp1 should NOT have run
    }

    @Test
    void isSuccess_reflectsState() {
        MergeTransaction tx = new MergeTransaction();
        assertFalse(tx.isSuccess());
        tx.markSuccess();
        assertTrue(tx.isSuccess());
        tx.close();
    }

    @Test
    void executeAfterClose_throwsIllegalState() {
        MergeTransaction tx = new MergeTransaction();
        tx.markSuccess();
        tx.close();

        assertThrows(IllegalStateException.class, () ->
                tx.execute("step", () -> {}, () -> {}));
    }

    @Test
    void executeNoCompensationAfterClose_throwsIllegalState() {
        MergeTransaction tx = new MergeTransaction();
        tx.markSuccess();
        tx.close();

        assertThrows(IllegalStateException.class, () ->
                tx.executeNoCompensation("step", () -> {}));
    }

    @Test
    void executeNoCompensation_failureTriggersCompensationsForPriorSteps() {
        List<String> log = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> {
            try (MergeTransaction tx = new MergeTransaction()) {
                tx.execute("step1", () -> log.add("op1"), () -> log.add("comp1"));
                tx.executeNoCompensation("step2", () -> {
                    throw new RuntimeException("step2 failed");
                });
            }
        });

        // op1 ran, step2 failed, comp1 ran as compensation
        assertEquals(List.of("op1", "comp1"), log);
    }

    @Test
    void emptyTransaction_succeedsWithoutIssue() {
        try (MergeTransaction tx = new MergeTransaction()) {
            tx.markSuccess();
        }
        // No exceptions
    }
}
