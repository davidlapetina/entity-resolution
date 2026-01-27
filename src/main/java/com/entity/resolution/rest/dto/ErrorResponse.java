package com.entity.resolution.rest.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response DTO.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> details
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now(), null);
    }

    public ErrorResponse(int status, String error, String message, String path,
                          Map<String, String> details) {
        this(status, error, message, path, Instant.now(), details);
    }

    public static ErrorResponse badRequest(String message, String path) {
        return new ErrorResponse(400, "Bad Request", message, path);
    }

    public static ErrorResponse notFound(String message, String path) {
        return new ErrorResponse(404, "Not Found", message, path);
    }

    public static ErrorResponse conflict(String message, String path) {
        return new ErrorResponse(409, "Conflict", message, path);
    }

    public static ErrorResponse internalError(String message, String path) {
        return new ErrorResponse(500, "Internal Server Error", message, path);
    }
}
