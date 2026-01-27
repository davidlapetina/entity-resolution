package com.entity.resolution.bulk;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolutionService;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonBulkImporterTest {

    @Mock
    private EntityResolutionService resolutionService;

    private JsonBulkImporter importer;

    @BeforeEach
    void setUp() {
        importer = new JsonBulkImporter(resolutionService);
    }

    @Test
    @DisplayName("Should import JSON Lines format")
    void testJsonLines() {
        String jsonl = """
                {"name": "Acme Corp"}
                {"name": "Big Blue"}
                {"name": "Microsoft"}
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        ImportResult result = importer.importEntities(new StringReader(jsonl), EntityType.COMPANY, null);

        assertEquals(3, result.totalRecords());
        assertEquals(3, result.entitiesCreated());
        verify(resolutionService, times(3)).resolve(anyString(), eq(EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should import JSON array format")
    void testJsonArray() {
        String json = """
                [
                  {"name": "Acme Corp"},
                  {"name": "Big Blue"},
                  {"name": "Microsoft"}
                ]
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        ImportResult result = importer.importEntities(new StringReader(json), EntityType.COMPANY, null);

        assertEquals(3, result.totalRecords());
        assertEquals(3, result.entitiesCreated());
    }

    @Test
    @DisplayName("Should skip lines without name field")
    void testSkipInvalidLines() {
        String jsonl = """
                {"name": "Acme Corp"}
                {"invalid": "no name field"}
                {"name": "Big Blue"}
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(anyString(), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));

        ImportResult result = importer.importEntities(new StringReader(jsonl), EntityType.COMPANY, null);

        assertEquals(2, result.totalRecords());
    }

    @Test
    @DisplayName("Should handle empty input")
    void testEmptyInput() {
        ImportResult result = importer.importEntities(new StringReader(""), EntityType.COMPANY, null);
        assertEquals(0, result.totalRecords());
    }

    @Test
    @DisplayName("Should track errors for failed resolutions")
    void testErrorTracking() {
        String jsonl = """
                {"name": "Good Corp"}
                {"name": "Bad Corp"}
                """;

        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        when(resolutionService.resolve(eq("Good Corp"), eq(EntityType.COMPANY)))
                .thenReturn(EntityResolutionResult.newEntity(entity));
        when(resolutionService.resolve(eq("Bad Corp"), eq(EntityType.COMPANY)))
                .thenThrow(new RuntimeException("Database error"));

        ImportResult result = importer.importEntities(new StringReader(jsonl), EntityType.COMPANY, null);

        assertEquals(2, result.totalRecords());
        assertEquals(1, result.entitiesCreated());
        assertEquals(1, result.errorCount());
    }

    @Test
    @DisplayName("Should report correct format")
    void testFormat() {
        assertEquals("json", importer.getFormat());
    }
}
