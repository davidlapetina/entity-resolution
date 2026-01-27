package com.entity.resolution.bulk;

import com.entity.resolution.core.model.EntityType;

import java.io.InputStream;
import java.io.Reader;

/**
 * Interface for bulk import operations.
 * Implementations read entities from a specific format (CSV, JSON, etc.)
 * and resolve them using the entity resolution service.
 */
public interface BulkImporter {

    /**
     * Imports entities from an input stream.
     *
     * @param input       the input stream to read from
     * @param entityType  the entity type to assign to imported entities
     * @param callback    optional progress callback
     * @return the import result
     */
    ImportResult importEntities(InputStream input, EntityType entityType, ProgressCallback callback);

    /**
     * Imports entities from a reader.
     *
     * @param reader      the reader to read from
     * @param entityType  the entity type to assign to imported entities
     * @param callback    optional progress callback
     * @return the import result
     */
    ImportResult importEntities(Reader reader, EntityType entityType, ProgressCallback callback);

    /**
     * Returns the format supported by this importer (e.g., "csv", "json").
     */
    String getFormat();
}
