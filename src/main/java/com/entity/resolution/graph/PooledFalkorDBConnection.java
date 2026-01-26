package com.entity.resolution.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * A pool-aware {@link GraphConnection} that borrows a connection per operation
 * and releases it back to the pool when done.
 *
 * <p>This is transparent to {@link CypherExecutor} and all repositories --
 * they call execute/query as normal, and pool management happens behind the scenes.</p>
 */
public class PooledFalkorDBConnection implements GraphConnection {
    private static final Logger log = LoggerFactory.getLogger(PooledFalkorDBConnection.class);

    private final GraphConnectionPool pool;
    private final String graphName;

    public PooledFalkorDBConnection(GraphConnectionPool pool, String graphName) {
        this.pool = pool;
        this.graphName = graphName;
    }

    @Override
    public void execute(String query, Map<String, Object> params) {
        GraphConnection conn = pool.borrow();
        try {
            conn.execute(query, params);
        } finally {
            pool.release(conn);
        }
    }

    @Override
    public List<Map<String, Object>> query(String query, Map<String, Object> params) {
        GraphConnection conn = pool.borrow();
        try {
            return conn.query(query, params);
        } finally {
            pool.release(conn);
        }
    }

    @Override
    public boolean isConnected() {
        GraphConnection conn = pool.borrow();
        try {
            return conn.isConnected();
        } finally {
            pool.release(conn);
        }
    }

    @Override
    public String getGraphName() {
        return graphName;
    }

    @Override
    public void createIndexes() {
        GraphConnection conn = pool.borrow();
        try {
            conn.createIndexes();
        } finally {
            pool.release(conn);
        }
    }

    @Override
    public void close() {
        // Closing the pooled connection closes the pool itself
        pool.close();
    }
}
