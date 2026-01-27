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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON Lines (JSONL) bulk importer.
 *
 * <p>Expected format: one JSON object per line.</p>
 * <pre>
 * {"name": "Acme Corp"}
 * {"name": "Big Blue"}
 * {"name": "International Business Machines"}
 * </pre>
 *
 * <p>Or a JSON array in the input stream:</p>
 * <pre>
 * [
 *   {"name": "Acme Corp"},
 *   {"name": "Big Blue"}
 * ]
 * </pre>
 *
 * <p>Uses simple regex-based parsing to avoid Jackson dependency on import path.
 * For more complex JSON structures, use Jackson ObjectMapper directly.</p>
 */
public class JsonBulkImporter implements BulkImporter {
    private static final Logger log = LoggerFactory.getLogger(JsonBulkImporter.class);
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final int PROGRESS_INTERVAL = 100;

    private final EntityResolutionService resolutionService;
    private final ResolutionOptions options;

    public JsonBulkImporter(EntityResolutionService resolutionService) {
        this(resolutionService, null);
    }

    public JsonBulkImporter(EntityResolutionService resolutionService, ResolutionOptions options) {
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
            String line;
            long lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip array brackets, commas, and empty lines
                if (line.isEmpty() || line.equals("[") || line.equals("]") || line.equals(",")) {
                    continue;
                }
                // Remove trailing comma
                if (line.endsWith(",")) {
                    line = line.substring(0, line.length() - 1);
                }

                Matcher matcher = NAME_PATTERN.matcher(line);
                if (!matcher.find()) {
                    continue;
                }

                String name = matcher.group(1);
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
        return "json";
    }
}
