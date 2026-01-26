package com.entity.resolution.merge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Compensating transaction for merge operations.
 * Records compensation actions that are executed in reverse order
 * if any step fails or the transaction is not marked as successful.
 *
 * <p>Usage:</p>
 * <pre>
 * try (MergeTransaction tx = new MergeTransaction()) {
 *     tx.execute("create synonym", () -> createSynonym(...), () -> deleteSynonym(...));
 *     tx.execute("create duplicate", () -> createDuplicate(...), () -> deleteDuplicate(...));
 *     tx.markSuccess();
 * }
 * // If markSuccess() was not called, all compensations run in reverse order
 * </pre>
 */
public class MergeTransaction implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MergeTransaction.class);

    private final Deque<CompensatingAction> compensationStack = new ArrayDeque<>();
    private boolean success = false;
    private boolean closed = false;

    /**
     * Executes an operation and registers its compensation action.
     * If the operation fails, all previously registered compensations
     * are executed in reverse order, and the exception is rethrown.
     *
     * @param description human-readable description of the step
     * @param operation   the operation to perform
     * @param compensation the action to reverse the operation
     * @throws RuntimeException if the operation fails (after running compensations)
     */
    public void execute(String description, Runnable operation, Runnable compensation) {
        if (closed) {
            throw new IllegalStateException("Transaction is already closed");
        }

        try {
            log.debug("Executing merge step: {}", description);
            operation.run();
            compensationStack.push(new CompensatingAction(description, compensation));
        } catch (Exception e) {
            log.warn("Merge step '{}' failed: {}. Running compensations.", description, e.getMessage());
            runCompensations();
            throw e;
        }
    }

    /**
     * Executes an operation without a compensation action.
     * Used for append-only operations (audit, ledger) that cannot be undone.
     *
     * @param description human-readable description of the step
     * @param operation   the operation to perform
     * @throws RuntimeException if the operation fails (after running compensations)
     */
    public void executeNoCompensation(String description, Runnable operation) {
        if (closed) {
            throw new IllegalStateException("Transaction is already closed");
        }

        try {
            log.debug("Executing merge step (no compensation): {}", description);
            operation.run();
        } catch (Exception e) {
            log.warn("Merge step '{}' failed: {}. Running compensations.", description, e.getMessage());
            runCompensations();
            throw e;
        }
    }

    /**
     * Marks the transaction as successful.
     * If called before close(), compensations will not be executed.
     */
    public void markSuccess() {
        this.success = true;
    }

    /**
     * Returns whether the transaction was marked as successful.
     */
    public boolean isSuccess() {
        return success;
    }

    @Override
    public void close() {
        if (!closed && !success) {
            log.warn("MergeTransaction closed without success - running compensations");
            runCompensations();
        }
        closed = true;
    }

    private void runCompensations() {
        while (!compensationStack.isEmpty()) {
            CompensatingAction action = compensationStack.pop();
            try {
                log.debug("Running compensation: {}", action.description);
                action.compensation.run();
            } catch (Exception e) {
                log.error("Compensation '{}' failed (best-effort): {}", action.description, e.getMessage());
                // Continue running remaining compensations
            }
        }
    }

    private record CompensatingAction(String description, Runnable compensation) {}
}
