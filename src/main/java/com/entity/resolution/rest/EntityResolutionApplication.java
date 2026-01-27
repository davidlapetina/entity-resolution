package com.entity.resolution.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * Jakarta RS Application class with OpenAPI metadata.
 *
 * <p>Defines the OpenAPI specification for the Entity Resolution REST API,
 * including the API key security scheme used across all endpoints.</p>
 */
@ApplicationPath("/")
@OpenAPIDefinition(
        info = @Info(
                title = "Entity Resolution API",
                version = "1.0.0",
                description = "Graph-native entity resolution service providing deterministic and " +
                        "probabilistic deduplication, synonym management, and optional LLM-assisted " +
                        "semantic enrichment. Built on FalkorDB.",
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0.html"
                ),
                contact = @Contact(
                        name = "Entity Resolution Contributors",
                        url = "https://github.com/entity-resolution/entity-resolution"
                )
        )
)
@SecurityScheme(
        securitySchemeName = "apiKey",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "X-API-Key",
        in = SecuritySchemeIn.HEADER,
        description = "API key for authentication. Keys are mapped to roles: READER, WRITER, ADMIN."
)
public class EntityResolutionApplication extends Application {
}
