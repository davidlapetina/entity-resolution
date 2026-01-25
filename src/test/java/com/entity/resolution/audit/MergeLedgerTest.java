package com.entity.resolution.audit;

import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MergeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MergeLedgerTest {

    private MergeLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new MergeLedger();
    }

    @Test
    @DisplayName("Should record merge operations")
    void testRecordMerge() {
        MergeRecord record = ledger.recordMerge(
                "source-1", "target-1",
                "Source Corp", "Target Corp",
                0.95, MatchDecision.AUTO_MERGE,
                "system", "High similarity match"
        );

        assertEquals(1, ledger.size());
        assertEquals("source-1", record.sourceEntityId());
        assertEquals("target-1", record.targetEntityId());
        assertEquals(0.95, record.confidenceScore());
        assertEquals(MatchDecision.AUTO_MERGE, record.decision());
    }

    @Test
    @DisplayName("Should get records by target entity")
    void testGetByTarget() {
        ledger.recordMerge("s1", "target", "S1", "Target", 0.90, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("s2", "target", "S2", "Target", 0.92, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("s3", "other", "S3", "Other", 0.95, MatchDecision.AUTO_MERGE, "sys", null);

        var records = ledger.getRecordsForTarget("target");
        assertEquals(2, records.size());
    }

    @Test
    @DisplayName("Should get records by source entity")
    void testGetBySource() {
        ledger.recordMerge("source", "t1", "Source", "T1", 0.90, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("source", "t2", "Source", "T2", 0.92, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("other", "t3", "Other", "T3", 0.95, MatchDecision.AUTO_MERGE, "sys", null);

        var records = ledger.getRecordsForSource("source");
        assertEquals(2, records.size());
    }

    @Test
    @DisplayName("Should get records by decision type")
    void testGetByDecision() {
        ledger.recordMerge("s1", "t1", "S1", "T1", 0.95, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("s2", "t2", "S2", "T2", 0.85, MatchDecision.SYNONYM_ONLY, "sys", null);
        ledger.recordMerge("s3", "t3", "S3", "T3", 0.96, MatchDecision.AUTO_MERGE, "sys", null);

        var autoMerges = ledger.getRecordsByDecision(MatchDecision.AUTO_MERGE);
        assertEquals(2, autoMerges.size());
    }

    @Test
    @DisplayName("Should build merge chain")
    void testMergeChain() {
        // Entity chain: s1 -> s2 -> canonical
        ledger.recordMerge("s1", "s2", "S1", "S2", 0.90, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("s2", "canonical", "S2", "Canonical", 0.92, MatchDecision.AUTO_MERGE, "sys", null);
        ledger.recordMerge("s3", "canonical", "S3", "Canonical", 0.95, MatchDecision.AUTO_MERGE, "sys", null);

        List<String> chain = ledger.getMergeChain("canonical");

        // Should include s2, s1, and s3 (s1 is in chain because s2 was merged into canonical)
        assertEquals(3, chain.size());
        assertTrue(chain.contains("s2"));
        assertTrue(chain.contains("s3"));
        assertTrue(chain.contains("s1"));
    }

    @Test
    @DisplayName("Should return immutable records list")
    void testImmutableRecords() {
        ledger.recordMerge("s1", "t1", "S1", "T1", 0.95, MatchDecision.AUTO_MERGE, "sys", null);

        var records = ledger.getAllRecords();
        assertThrows(UnsupportedOperationException.class, () ->
                records.add(MergeRecord.builder()
                        .sourceEntityId("x")
                        .targetEntityId("y")
                        .decision(MatchDecision.AUTO_MERGE)
                        .build())
        );
    }

    @Test
    @DisplayName("Should filter by actor")
    void testGetByActor() {
        ledger.recordMerge("s1", "t1", "S1", "T1", 0.95, MatchDecision.AUTO_MERGE, "user1", null);
        ledger.recordMerge("s2", "t2", "S2", "T2", 0.90, MatchDecision.AUTO_MERGE, "user2", null);
        ledger.recordMerge("s3", "t3", "S3", "T3", 0.92, MatchDecision.AUTO_MERGE, "user1", null);

        var records = ledger.getRecordsByActor("user1");
        assertEquals(2, records.size());
    }
}
