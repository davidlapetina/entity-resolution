package com.entity.resolution.audit;

import com.entity.resolution.api.CursorPage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AuditRepository.
 * Thread-safe via CopyOnWriteArrayList.
 * This is the default implementation used for backward compatibility.
 */
public class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public AuditEntry save(AuditEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public List<AuditEntry> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    @Override
    public List<AuditEntry> findByEntityId(String entityId) {
        return entries.stream()
                .filter(e -> entityId.equals(e.entityId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> findByAction(AuditAction action) {
        return entries.stream()
                .filter(e -> e.action() == action)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> findByActorId(String actorId) {
        return entries.stream()
                .filter(e -> actorId.equals(e.actorId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> findBetween(Instant start, Instant end) {
        return entries.stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> findByEntityIdBetween(String entityId, Instant start, Instant end) {
        return entries.stream()
                .filter(e -> entityId.equals(e.entityId()))
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public int count() {
        return entries.size();
    }

    @Override
    public List<AuditEntry> findRecent(int limit) {
        int size = entries.size();
        if (size <= limit) {
            return findAll();
        }
        return Collections.unmodifiableList(
                new ArrayList<>(entries.subList(size - limit, size))
        );
    }

    @Override
    public CursorPage<AuditEntry> findByEntityIdAfter(String entityId, String cursor, int limit) {
        Instant cursorInstant = (cursor != null && !cursor.isEmpty()) ? Instant.parse(cursor) : null;
        List<AuditEntry> filtered = entries.stream()
                .filter(e -> entityId.equals(e.entityId()))
                .filter(e -> cursorInstant == null || e.timestamp().isAfter(cursorInstant))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .limit(limit + 1)
                .toList();
        boolean hasMore = filtered.size() > limit;
        List<AuditEntry> content = hasMore ? filtered.subList(0, limit) : filtered;
        String nextCursor = hasMore ? content.get(content.size() - 1).timestamp().toString() : null;
        return new CursorPage<>(content, nextCursor, hasMore);
    }
}
