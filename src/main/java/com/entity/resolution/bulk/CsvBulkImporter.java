package com.entity.resolution.bulk;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolutionService;
import com.entity.resolution.api.ResolutionOptions;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV bulk importer.
 *
 * <p>Expected CSV format:</p>
 * <pre>
 * name
 * "Acme Corp"
 * "Big Blue"
 * "International Business Machines"
 * </pre>
 *
 * <p>The first line is treated as a header row (skipped).
 * Each subsequent line contains an entity name to resolve.</p>
 */
public class CsvBulkImporter implements BulkImporter {
    private static final Logger log = LoggerFactory.getLogger(CsvBulkImporter.class);
    private static final int PROGRESS_INTERVAL = 100;

    private final EntityResolutionService resolutionService;
    private final ResolutionOptions options;

    public CsvBulkImporter(EntityResolutionService resolutionService) {
        this(resolutionService, null);
    }

    public CsvBulkImporter(EntityResolutionService resolutionService, ResolutionOptions options) {
        this.resolutionService = resolutionService;
        this.options = options;
    }

    @Override
    public ImportResult importEntities(InputStream input, EntityType entityType, ProgressCallback callback) {
        return importEntities(new InputStreamReader(input), entityType, callback);
    }

    @Override
    public ImportResult importEntities(Reader reader, EntityType entityType, ProgressCallback callback) {
        ProgressCallback cb = callback != null ? callback : ProgressCallback.NOOP;
        List<ImportResult.ImportError> errors = new ArrayList<>();

        long totalRecords = 0;
        long entitiesCreated = 0;
        long entitiesMatched = 0;
        long entitiesMerged = 0;
        long reviewsCreated = 0;

        try (BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
            // Skip header
            String header = br.readLine();
            if (header == null) {
                return new ImportResult(0, 0, 0, 0, 0, List.of());
            }

            String line;
            long lineNumber = 1;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                String name = parseCsvField(line.trim());
                if (name.isEmpty()) {
                    continue;
                }
                totalRecords++;

                try {
                    EntityResolutionResult result;
                    if (options != null) {
                        result = resolutionService.resolve(name, entityType, options);
                    } else {
                        result = resolutionService.resolve(name, entityType);
                    }

                    if (result.isNewEntity()) {
                        entitiesCreated++;
                    } else if (result.wasMerged()) {
                        entitiesMerged++;
                    } else if (result.getDecision() == MatchDecision.REVIEW) {
                        reviewsCreated++;
                    } else {
                        entitiesMatched++;
                    }
                } catch (Exception e) {
                    errors.add(new ImportResult.ImportError(lineNumber, name, e.getMessage()));
                    log.warn("import.error line={} name='{}' error={}", lineNumber, name, e.getMessage());
                }

                if (totalRecords % PROGRESS_INTERVAL == 0) {
                    cb.onProgress(totalRecords, -1, "Processed " + totalRecords + " records");
                }
            }
        } catch (IOException e) {
            log.error("import.failed error={}", e.getMessage());
            errors.add(new ImportResult.ImportError(0, "", "IO error: " + e.getMessage()));
        }

        ImportResult result = new ImportResult(totalRecords, entitiesCreated, entitiesMatched,
                entitiesMerged, reviewsCreated, errors);
        cb.onProgress(totalRecords, totalRecords, "Import completed");
        log.info("import.completed result={}", result);
        return result;
    }

    @Override
    public String getFormat() {
        return "csv";
    }

    /**
     * Parses a CSV field, handling quoted values.
     */
    private String parseCsvField(String field) {
        if (field.startsWith("\"") && field.endsWith("\"")) {
            return field.substring(1, field.length() - 1).replace("\"\"", "\"");
        }
        return field;
    }
}
