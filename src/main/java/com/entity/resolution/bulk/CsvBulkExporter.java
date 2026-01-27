package com.entity.resolution.bulk;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.Relationship;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.graph.EntityRepository;
import com.entity.resolution.graph.RelationshipRepository;
import com.entity.resolution.graph.SynonymRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

/**
 * CSV bulk exporter.
 * Exports entities, synonyms, and relationships in CSV format using streaming
 * with paginated queries to handle large datasets.
 *
 * <p>Output format:</p>
 * <pre>
 * # ENTITIES
 * id,canonicalName,normalizedName,type,confidenceScore,status
 * "uuid-1","Acme Corp","acme corp","COMPANY",1.0,"ACTIVE"
 *
 * # SYNONYMS
 * entityId,value,normalizedValue,source,confidence
 * "uuid-1","Acme Corporation","acme corporation","SYSTEM",0.95
 *
 * # RELATIONSHIPS
 * sourceEntityId,targetEntityId,type,createdAt
 * "uuid-1","uuid-2","PARTNER","2024-01-01T00:00:00Z"
 * </pre>
 */
public class CsvBulkExporter implements BulkExporter {
    private static final Logger log = LoggerFactory.getLogger(CsvBulkExporter.class);
    private static final int PAGE_SIZE = 500;

    private final EntityRepository entityRepository;
    private final SynonymRepository synonymRepository;
    private final RelationshipRepository relationshipRepository;

    public CsvBulkExporter(EntityRepository entityRepository,
                           SynonymRepository synonymRepository,
                           RelationshipRepository relationshipRepository) {
        this.entityRepository = entityRepository;
        this.synonymRepository = synonymRepository;
        this.relationshipRepository = relationshipRepository;
    }

    @Override
    public ExportResult exportEntities(OutputStream output, EntityType entityType, ProgressCallback callback) {
        return exportEntities(new OutputStreamWriter(output), entityType, callback);
    }

    @Override
    public ExportResult exportEntities(Writer writer, EntityType entityType, ProgressCallback callback) {
        ProgressCallback cb = callback != null ? callback : ProgressCallback.NOOP;
        PrintWriter pw = new PrintWriter(new BufferedWriter(writer));

        long totalEntities = 0;
        long totalSynonyms = 0;
        long totalRelationships = 0;

        try {
            EntityType[] types = entityType != null
                    ? new EntityType[]{entityType}
                    : EntityType.values();

            // Export entities
            pw.println("# ENTITIES");
            pw.println("id,canonicalName,normalizedName,type,confidenceScore,status");

            for (EntityType type : types) {
                int page = 0;
                boolean hasMore = true;
                while (hasMore) {
                    PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);
                    Page<Entity> entityPage = entityRepository.findAllActive(type, pageRequest);
                    for (Entity entity : entityPage.content()) {
                        pw.printf("%s,%s,%s,%s,%.2f,%s%n",
                                csvEscape(entity.getId()),
                                csvEscape(entity.getCanonicalName()),
                                csvEscape(entity.getNormalizedName()),
                                csvEscape(entity.getType().name()),
                                entity.getConfidenceScore(),
                                csvEscape(entity.getStatus().name()));
                        totalEntities++;
                    }
                    hasMore = entityPage.hasNext();
                    page++;

                    cb.onProgress(totalEntities, -1, "Exported " + totalEntities + " entities");
                }
            }

            // Export synonyms
            pw.println();
            pw.println("# SYNONYMS");
            pw.println("entityId,value,normalizedValue,source,confidence");

            for (EntityType type : types) {
                int page = 0;
                boolean hasMore = true;
                while (hasMore) {
                    PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);
                    Page<Entity> entityPage = entityRepository.findAllActive(type, pageRequest);
                    for (Entity entity : entityPage.content()) {
                        List<Synonym> synonyms = synonymRepository.findByEntityId(entity.getId());
                        for (Synonym synonym : synonyms) {
                            pw.printf("%s,%s,%s,%s,%.2f%n",
                                    csvEscape(entity.getId()),
                                    csvEscape(synonym.getValue()),
                                    csvEscape(synonym.getNormalizedValue()),
                                    csvEscape(synonym.getSource().name()),
                                    synonym.getConfidence());
                            totalSynonyms++;
                        }
                    }
                    hasMore = entityPage.hasNext();
                    page++;
                }
            }

            // Export relationships
            if (relationshipRepository != null) {
                pw.println();
                pw.println("# RELATIONSHIPS");
                pw.println("sourceEntityId,targetEntityId,type,createdAt");

                for (EntityType type : types) {
                    int page = 0;
                    boolean hasMore = true;
                    while (hasMore) {
                        PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE);
                        Page<Entity> entityPage = entityRepository.findAllActive(type, pageRequest);
                        for (Entity entity : entityPage.content()) {
                            List<Relationship> rels = relationshipRepository.findBySourceEntity(entity.getId());
                            for (Relationship rel : rels) {
                                pw.printf("%s,%s,%s,%s%n",
                                        csvEscape(rel.getSourceEntityId()),
                                        csvEscape(rel.getTargetEntityId()),
                                        csvEscape(rel.getRelationshipType()),
                                        csvEscape(rel.getCreatedAt().toString()));
                                totalRelationships++;
                            }
                        }
                        hasMore = entityPage.hasNext();
                        page++;
                    }
                }
            }

            pw.flush();
        } catch (Exception e) {
            log.error("export.failed error={}", e.getMessage());
        }

        ExportResult result = new ExportResult(totalEntities, totalSynonyms, totalRelationships);
        cb.onProgress(totalEntities, totalEntities, "Export completed");
        log.info("export.completed result={}", result);
        return result;
    }

    @Override
    public String getFormat() {
        return "csv";
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
