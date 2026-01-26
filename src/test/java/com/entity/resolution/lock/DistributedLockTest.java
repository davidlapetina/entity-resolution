package com.entity.resolution.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DistributedLockTest {

    @Nested
    @DisplayName("NoOpDistributedLock")
    class NoOpTests {

        @Test
        @DisplayName("Should always acquire lock")
        void testAlwaysAcquires() {
            NoOpDistributedLock lock = new NoOpDistributedLock();
            assertTrue(lock.tryLock("any-key"));
        }

        @Test
        @DisplayName("Should unlock without error")
        void testUnlockNoOp() {
            NoOpDistributedLock lock = new NoOpDistributedLock();
            assertDoesNotThrow(() -> lock.unlock("any-key"));
        }
    }

    @Nested
    @DisplayName("LocalDistributedLock")
    class LocalLockTests {

        @Test
        @DisplayName("Should acquire and release lock")
        void testAcquireRelease() {
            LocalDistributedLock lock = new LocalDistributedLock();
            assertTrue(lock.tryLock("test-key"));
            assertDoesNotThrow(() -> lock.unlock("test-key"));
        }

        @Test
        @DisplayName("Should allow re-entrant locking from same thread")
        void testReentrant() {
            LocalDistributedLock lock = new LocalDistributedLock();
            assertTrue(lock.tryLock("test-key"));
            assertTrue(lock.tryLock("test-key")); // re-entrant
            lock.unlock("test-key");
            lock.unlock("test-key");
        }

        @Test
        @DisplayName("Should allow different keys concurrently")
        void testDifferentKeys() {
            LocalDistributedLock lock = new LocalDistributedLock();
            assertTrue(lock.tryLock("key-1"));
            assertTrue(lock.tryLock("key-2"));
            lock.unlock("key-1");
            lock.unlock("key-2");
        }

        @Test
        @DisplayName("Should block concurrent access to same key")
        void testConcurrentBlocking() throws Exception {
            LocalDistributedLock lock = new LocalDistributedLock(
                    new LockConfig(2000, 0, 100, 30));
            AtomicInteger concurrentCount = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);

            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        lock.tryLock("shared-key");
                        try {
                            int current = concurrentCount.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, current));
                            Thread.sleep(50); // simulate work
                            concurrentCount.decrementAndGet();
                        } finally {
                            lock.unlock("shared-key");
                        }
                    } catch (Exception e) {
                        // Lock acquisition timeout is expected for some threads
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            // At most 1 thread should hold the lock at a time
            assertEquals(1, maxConcurrent.get());
        }

        @Test
        @DisplayName("Should handle unlock for non-existent key gracefully")
        void testUnlockNonExistentKey() {
            LocalDistributedLock lock = new LocalDistributedLock();
            assertDoesNotThrow(() -> lock.unlock("non-existent"));
        }
    }

    @Nested
    @DisplayName("LockConfig")
    class LockConfigTests {

        @Test
        @DisplayName("Should create default config")
        void testDefaults() {
            LockConfig config = LockConfig.defaults();
            assertEquals(5000, config.timeoutMs());
            assertEquals(3, config.maxRetries());
            assertEquals(100, config.retryDelayMs());
            assertEquals(30, config.lockTtlSeconds());
        }

        @Test
        @DisplayName("Should reject invalid timeout")
        void testInvalidTimeout() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LockConfig(0, 3, 100, 30));
        }

        @Test
        @DisplayName("Should reject negative retries")
        void testNegativeRetries() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LockConfig(5000, -1, 100, 30));
        }
    }

    @Nested
    @DisplayName("LockAcquisitionException")
    class ExceptionTests {

        @Test
        @DisplayName("Should carry message")
        void testMessage() {
            LockAcquisitionException ex = new LockAcquisitionException("lock failed");
            assertEquals("lock failed", ex.getMessage());
        }

        @Test
        @DisplayName("Should carry cause")
        void testCause() {
            RuntimeException cause = new RuntimeException("root cause");
            LockAcquisitionException ex = new LockAcquisitionException("lock failed", cause);
            assertEquals(cause, ex.getCause());
        }
    }
}
