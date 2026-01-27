package com.entity.resolution.integration;

import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.graph.FalkorDBConnection;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * Base class for FalkorDB integration tests using Testcontainers.
 * Provides a shared FalkorDB container and helper methods for creating
 * resolvers with isolated graph names.
 */
@Tag("integration")
@Testcontainers
abstract class AbstractFalkorDBIntegrationTest {

    private static final int FALKORDB_PORT = 6379;

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> falkorDB = new GenericContainer<>("falkordb/falkordb:latest")
            .withExposedPorts(FALKORDB_PORT);

    /**
     * Creates a new EntityResolver connected to the test FalkorDB container
     * with a unique graph name to isolate tests.
     *
     * @param graphNamePrefix prefix for the graph name (a UUID suffix is appended)
     * @return a new EntityResolver instance
     */
    protected EntityResolver createResolver(String graphNamePrefix) {
        String graphName = graphNamePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        FalkorDBConnection connection = new FalkorDBConnection(
                falkorDB.getHost(),
                falkorDB.getMappedPort(FALKORDB_PORT),
                graphName
        );
        return EntityResolver.builder()
                .graphConnection(connection)
                .build();
    }

    /**
     * Creates a new EntityResolver with a fully unique graph name.
     *
     * @return a new EntityResolver instance
     */
    protected EntityResolver createResolver() {
        return createResolver("integration-test");
    }
}
