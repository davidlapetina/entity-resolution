package com.entity.resolution.api;

import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditEntry;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.audit.InMemoryAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginationTest {

    @Nested
    @DisplayName("PageRequest")
    class PageRequestTests {

        @Test
        @DisplayName("Should create page request from page and size")
        void testOf() {
            PageRequest request = PageRequest.of(2, 10);
            assertEquals(20, request.offset());
            assertEquals(10, request.limit());
            assertEquals(2, request.pageNumber());
        }

        @Test
        @DisplayName("Should create first page request")
        void testFirst() {
            PageRequest request = PageRequest.first(25);
            assertEquals(0, request.offset());
            assertEquals(25, request.limit());
            assertEquals(0, request.pageNumber());
        }

        @Test
        @DisplayName("Should reject negative offset")
        void testNegativeOffset() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PageRequest(-1, 10, null, PageRequest.SortDirection.ASC));
        }

        @Test
        @DisplayName("Should reject zero limit")
        void testZeroLimit() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PageRequest(0, 0, null, PageRequest.SortDirection.ASC));
        }

        @Test
        @DisplayName("Should reject limit exceeding maximum")
        void testExcessiveLimit() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PageRequest(0, 10_001, null, PageRequest.SortDirection.ASC));
        }

        @Test
        @DisplayName("Should reject negative page number")
        void testNegativePage() {
            assertThrows(IllegalArgumentException.class,
                    () -> PageRequest.of(-1, 10));
        }

        @Test
        @DisplayName("Should create with sort parameters")
        void testWithSort() {
            PageRequest request = PageRequest.of(0, 10, "canonicalName", PageRequest.SortDirection.DESC);
            assertEquals("canonicalName", request.sortBy());
            assertEquals(PageRequest.SortDirection.DESC, request.sortDirection());
        }
    }

    @Nested
    @DisplayName("Page")
    class PageTests {

        @Test
        @DisplayName("Should create page with content")
        void testPageCreation() {
            List<String> content = List.of("a", "b", "c");
            Page<String> page = new Page<>(content, 10, 0, 3);

            assertEquals(3, page.numberOfElements());
            assertEquals(10, page.totalElements());
            assertEquals(0, page.pageNumber());
            assertEquals(3, page.pageSize());
        }

        @Test
        @DisplayName("Should calculate total pages correctly")
        void testTotalPages() {
            Page<String> page = new Page<>(List.of("a"), 10, 0, 3);
            assertEquals(4, page.totalPages()); // ceil(10/3) = 4
        }

        @Test
        @DisplayName("Should detect has next page")
        void testHasNext() {
            Page<String> page = new Page<>(List.of("a", "b", "c"), 10, 0, 3);
            assertTrue(page.hasNext());

            Page<String> lastPage = new Page<>(List.of("j"), 10, 3, 3);
            assertFalse(lastPage.hasNext());
        }

        @Test
        @DisplayName("Should detect has previous page")
        void testHasPrevious() {
            Page<String> firstPage = new Page<>(List.of("a"), 10, 0, 3);
            assertFalse(firstPage.hasPrevious());

            Page<String> secondPage = new Page<>(List.of("d"), 10, 1, 3);
            assertTrue(secondPage.hasPrevious());
        }

        @Test
        @DisplayName("Should create empty page")
        void testEmptyPage() {
            Page<String> empty = Page.empty(PageRequest.first(10));
            assertFalse(empty.hasContent());
            assertEquals(0, empty.totalElements());
            assertEquals(0, empty.numberOfElements());
            assertFalse(empty.hasNext());
        }

        @Test
        @DisplayName("Should make defensive copy of content")
        void testDefensiveCopy() {
            var mutableList = new java.util.ArrayList<>(List.of("a", "b"));
            Page<String> page = new Page<>(mutableList, 2, 0, 10);
            assertThrows(UnsupportedOperationException.class, () -> page.content().add("c"));
        }

        @Test
        @DisplayName("Should reject negative total elements")
        void testNegativeTotalElements() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Page<>(List.of(), -1, 0, 10));
        }
    }

    @Nested
    @DisplayName("CursorPage")
    class CursorPageTests {

        @Test
        @DisplayName("Should create cursor page with content")
        void testCreation() {
            List<String> content = List.of("a", "b");
            CursorPage<String> page = new CursorPage<>(content, "2025-01-01T00:00:00Z", true);

            assertEquals(2, page.size());
            assertTrue(page.hasContent());
            assertTrue(page.hasMore());
            assertEquals("2025-01-01T00:00:00Z", page.nextCursor());
        }

        @Test
        @DisplayName("Should create empty cursor page")
        void testEmpty() {
            CursorPage<String> empty = CursorPage.empty();
            assertEquals(0, empty.size());
            assertFalse(empty.hasContent());
            assertFalse(empty.hasMore());
            assertNull(empty.nextCursor());
        }

        @Test
        @DisplayName("Should make defensive copy of content")
        void testDefensiveCopy() {
            var mutableList = new java.util.ArrayList<>(List.of("a"));
            CursorPage<String> page = new CursorPage<>(mutableList, null, false);
            assertThrows(UnsupportedOperationException.class, () -> page.content().add("b"));
        }
    }

    @Nested
    @DisplayName("AuditService Cursor Pagination")
    class AuditCursorPaginationTests {

        @Test
        @DisplayName("Should paginate audit entries by entity ID with cursor")
        void testCursorPagination() throws InterruptedException {
            InMemoryAuditRepository repo = new InMemoryAuditRepository();
            AuditService service = new AuditService(repo);

            // Create entries with distinct timestamps
            Instant base = Instant.parse("2025-01-01T00:00:00Z");
            for (int i = 0; i < 5; i++) {
                AuditEntry entry = AuditEntry.builder()
                        .action(AuditAction.ENTITY_CREATED)
                        .entityId("entity-1")
                        .actorId("test")
                        .timestamp(base.plusSeconds(i))
                        .build();
                service.record(entry);
            }

            // First page
            CursorPage<AuditEntry> page1 = service.getAuditTrailPaginated("entity-1", null, 2);
            assertEquals(2, page1.size());
            assertTrue(page1.hasMore());
            assertNotNull(page1.nextCursor());

            // Second page using cursor
            CursorPage<AuditEntry> page2 = service.getAuditTrailPaginated("entity-1", page1.nextCursor(), 2);
            assertEquals(2, page2.size());
            assertTrue(page2.hasMore());

            // Third page (last)
            CursorPage<AuditEntry> page3 = service.getAuditTrailPaginated("entity-1", page2.nextCursor(), 2);
            assertEquals(1, page3.size());
            assertFalse(page3.hasMore());
        }

        @Test
        @DisplayName("Should return empty cursor page for unknown entity")
        void testEmptyCursorPage() {
            InMemoryAuditRepository repo = new InMemoryAuditRepository();
            AuditService service = new AuditService(repo);

            CursorPage<AuditEntry> page = service.getAuditTrailPaginated("unknown", null, 10);
            assertEquals(0, page.size());
            assertFalse(page.hasMore());
            assertNull(page.nextCursor());
        }
    }
}
