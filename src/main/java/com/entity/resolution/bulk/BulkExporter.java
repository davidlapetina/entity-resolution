package com.entity.resolution.bulk;

import com.entity.resolution.core.model.EntityType;

import java.io.OutputStream;
import java.io.Writer;

/**
 * Interface for bulk export operations.
 * Implementations write entities and their relationships in a specific format.
 */
public interface BulkExporter {

    /**
     * Exports entities to an output stream.
     *
     * @param output     the output stream to write to
     * @param entityType the entity type to export (null for all)
     * @param callback   optional progress callback
     * @return the export result
     */
    ExportResult exportEntities(OutputStream output, EntityType entityType, ProgressCallback callback);

    /**
     * Exports entities to a writer.
     *
     * @param writer     the writer to write to
     * @param entityType the entity type to export (null for all)
     * @param callback   optional progress callback
     * @return the export result
     */
    ExportResult exportEntities(Writer writer, EntityType entityType, ProgressCallback callback);

    /**
     * Returns the format produced by this exporter (e.g., "csv", "json").
     */
    String getFormat();
}
