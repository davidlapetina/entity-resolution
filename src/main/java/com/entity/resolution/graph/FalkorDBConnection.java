package com.entity.resolution.graph;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import com.falkordb.ResultSet;
import com.falkordb.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * FalkorDB-specific implementation using JFalkorDB client.
 */
public class FalkorDBConnection implements GraphConnection {
    private static final Logger log = LoggerFactory.getLogger(FalkorDBConnection.class);

    private final Driver driver;
    private final Graph graph;
    private final String graphName;

    public FalkorDBConnection(String host, int port, String graphName) {
        this.driver = FalkorDB.driver(host, port);
        this.graphName = graphName;
        this.graph = driver.graph(graphName);
        log.info("FalkorDB connection initialized for graph: {}", graphName);
    }

    @Override
    public void execute(String query, Map<String, Object> params) {
        String processedQuery = processParams(query, params);
        log.debug("Executing: {}", processedQuery);
        graph.query(processedQuery);
    }

    @Override
    public List<Map<String, Object>> query(String query, Map<String, Object> params) {
        String processedQuery = processParams(query, params);
        log.debug("Querying: {}", processedQuery);

        ResultSet resultSet = graph.query(processedQuery);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Record record : resultSet) {
            Map<String, Object> row = new HashMap<>();
            for (String key : record.keys()) {
                row.put(key, record.getValue(key));
            }
            results.add(row);
        }

        log.debug("Query returned {} results", results.size());
        return results;
    }

    @Override
    public boolean isConnected() {
        try {
            // Test connection by executing a simple query
            graph.query("RETURN 1");
            return true;
        } catch (Exception e) {
            log.warn("Connection check failed", e);
            return false;
        }
    }

    @Override
    public String getGraphName() {
        return graphName;
    }

    @Override
    public void createIndexes() {
        log.info("Creating indexes for entity resolution...");

        // Entity indexes
        safeExecute("CREATE INDEX FOR (e:Entity) ON (e.id)");
        safeExecute("CREATE INDEX FOR (e:Entity) ON (e.normalizedName)");
        safeExecute("CREATE INDEX FOR (e:Entity) ON (e.canonicalName)");
        safeExecute("CREATE INDEX FOR (e:Entity) ON (e.type)");
        safeExecute("CREATE INDEX FOR (e:Entity) ON (e.status)");

        // Synonym indexes
        safeExecute("CREATE INDEX FOR (s:Synonym) ON (s.id)");
        safeExecute("CREATE INDEX FOR (s:Synonym) ON (s.normalizedValue)");

        // DuplicateEntity indexes
        safeExecute("CREATE INDEX FOR (d:DuplicateEntity) ON (d.id)");
        safeExecute("CREATE INDEX FOR (d:DuplicateEntity) ON (d.normalizedName)");

        log.info("Index creation complete");
    }

    private void safeExecute(String query) {
        try {
            graph.query(query);
        } catch (Exception e) {
            // Index might already exist or syntax differs
            log.debug("Index creation query result: {} - {}", query, e.getMessage());
        }
    }

    /**
     * Process parameter placeholders in query.
     * Substitutes $param syntax with actual values.
     */
    private String processParams(String query, Map<String, Object> params) {
        String result = query;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "$" + entry.getKey();
            String value = formatValue(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            // Escape single quotes and wrap in quotes
            return "'" + ((String) value).replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        return "'" + value.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Override
    public void close() {
        if (driver != null) {
            try {
                driver.close();
            } catch (Exception e) {
                log.warn("Error closing FalkorDB connection", e);
            }
        }
        log.info("FalkorDB connection closed");
    }
}
