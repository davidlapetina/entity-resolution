package com.entity.resolution.graph;

import java.util.List;
import java.util.Map;

/**
 * Interface for graph database connection management.
 * Abstracts the underlying graph database implementation.
 */
public interface GraphConnection extends AutoCloseable {

    /**
     * Executes a Cypher query that modifies the graph.
     *
     * @param query  the Cypher query
     * @param params query parameters
     */
    void execute(String query, Map<String, Object> params);

    /**
     * Executes a Cypher query that modifies the graph without parameters.
     *
     * @param query the Cypher query
     */
    default void execute(String query) {
        execute(query, Map.of());
    }

    /**
     * Executes a Cypher query and returns results.
     *
     * @param query  the Cypher query
     * @param params query parameters
     * @return list of result records as maps
     */
    List<Map<String, Object>> query(String query, Map<String, Object> params);

    /**
     * Executes a Cypher query without parameters and returns results.
     *
     * @param query the Cypher query
     * @return list of result records as maps
     */
    default List<Map<String, Object>> query(String query) {
        return query(query, Map.of());
    }

    /**
     * Checks if the connection is alive.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Gets the name of the graph being used.
     *
     * @return graph name
     */
    String getGraphName();

    /**
     * Creates indexes for entity resolution if they don't exist.
     */
    void createIndexes();
}
