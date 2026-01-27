package com.entity.resolution.bulk;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolutionService;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvBulkImporterTest {

    @Mock
    private EntityResolutionService resolutionService;

    private CsvBulkImporter importer;

    @BeforeEach
    void setUp() {
        importer = new CsvBulkImporter(resolutionService);
    }

    @Test
    @DisplayName("Should import CSV with header row")
    void testImportWithHeader() {
        String csv = """
                name
                Acme Corp
                Big Blue
                Microsoft
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        EntityResolutionResult newResult = EntityResolutionResult.newEntity(entity);

        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(newResult);

        ImportResult result = importer.importEntities(new StringReader(csv), EntityType.COMPANY, null);

        assertEquals(3, result.totalRecords());
        assertEquals(3, result.entitiesCreated());
        assertEquals(0, result.errorCount());
        verify(resolutionService, times(3)).resolve(anyString(), eq(EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should handle quoted CSV values")
    void testQuotedValues() {
        String csv = "name\n\"Acme, Corp\"\n\"Big \"\"Blue\"\"\"\n";

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        ImportResult result = importer.importEntities(new StringReader(csv), EntityType.COMPANY, null);

        assertEquals(2, result.totalRecords());
        verify(resolutionService).resolve(eq("Acme, Corp"), eq(EntityType.COMPANY));
        verify(resolutionService).resolve(eq("Big \"Blue\""), eq(EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should skip empty lines")
    void testSkipEmptyLines() {
        String csv = """
                name
                Acme Corp

                Big Blue
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        ImportResult result = importer.importEntities(new StringReader(csv), EntityType.COMPANY, null);

        assertEquals(2, result.totalRecords());
    }

    @Test
    @DisplayName("Should handle empty input")
    void testEmptyInput() {
        String csv = "";
        ImportResult result = importer.importEntities(new StringReader(csv), EntityType.COMPANY, null);
        assertEquals(0, result.totalRecords());
    }

    @Test
    @DisplayName("Should track errors for failed resolutions")
    void testErrorTracking() {
        String csv = """
                name
                Good Corp
                Bad Corp
                Another Good
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(eq("Good Corp"), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));
        when(resolutionService.resolve(eq("Bad Corp"), eq(EntityType.COMPANY)))
                .thenThrow(new RuntimeException("Database error"));
        when(resolutionService.resolve(eq("Another Good"), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        ImportResult result = importer.importEntities(new StringReader(csv), EntityType.COMPANY, null);

        assertEquals(3, result.totalRecords());
        assertEquals(2, result.entitiesCreated());
        assertEquals(1, result.errorCount());
        assertEquals("Bad Corp", result.errors().get(0).inputName());
    }

    @Test
    @DisplayName("Should track progress via callback")
    void testProgressCallback() {
        StringBuilder sb = new StringBuilder("name\n");
        for (int i = 0; i < 150; i++) {
            sb.append("Corp ").append(i).append("\n");
        }

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        List<Long> progressUpdates = new ArrayList<>();
        ProgressCallback callback = (processed, total, message) -> progressUpdates.add(processed);

        importer.importEntities(new StringReader(sb.toString()), EntityType.COMPANY, callback);

        // Should have at least one progress update at 100 and final
        assertTrue(progressUpdates.size() >= 2);
        assertTrue(progressUpdates.contains(100L));
    }

    @Test
    @DisplayName("Should report correct format")
    void testFormat() {
        assertEquals("csv", importer.getFormat());
    }

    @Test
    @DisplayName("Should categorize results correctly")
    void testResultCategorization() {
        String csv = """
                name
                New Entity
                Matched Entity
                """;

        Entity newEntity = Entity.builder()
                .canonicalName("New Entity")
                .type(EntityType.COMPANY)
                .build();
        Entity existingEntity = Entity.builder()
                .canonicalName("Matched Entity")
                .type(EntityType.COMPANY)
                .build();

        when(resolutionService.resolve(eq("New Entity"), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(newEntity));
        when(resolutionService.resolve(eq("Matched Entity"), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.exactMatch(existingEntity, List.of()));

        ImportResult result = importer.importEntities(new StringReader(csv), EntityType.COMPANY, null);

        assertEquals(2, result.totalRecords());
        assertEquals(1, result.entitiesCreated());
        assertEquals(1, result.entitiesMatched());
    }
}
