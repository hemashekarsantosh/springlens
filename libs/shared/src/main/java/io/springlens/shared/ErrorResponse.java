package io.springlens.shared;

import java.util.Map;

/**
 * Standard error response envelope returned by all SpringLens services.
 */
public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        String traceId) {

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, null, traceId);
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details, String traceId) {
        return new ErrorResponse(code, message, details, traceId);
    }
}
