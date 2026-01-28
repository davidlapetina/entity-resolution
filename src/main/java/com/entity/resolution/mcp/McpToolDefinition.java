package com.entity.resolution.mcp;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Definition of an MCP (Model Context Protocol) tool that can be exposed to LLM agents.
 * Each tool has a name, description, input schema, and a handler function.
 *
 * <p>All tools defined in this library are <strong>read-only</strong>.
 * LLMs cannot create, merge, or mutate entity data through these tools.</p>
 *
 * @param name        the tool name (e.g., "resolve_entity")
 * @param description a human-readable description of what the tool does
 * @param inputSchema the JSON Schema for the tool's input parameters
 * @param handler     the function that executes the tool, receiving input parameters and returning a result map
 */
public record McpToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Function<Map<String, Object>, Map<String, Object>> handler
) {
    public McpToolDefinition {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(inputSchema, "inputSchema is required");
        Objects.requireNonNull(handler, "handler is required");
        inputSchema = Map.copyOf(inputSchema);
    }
}
