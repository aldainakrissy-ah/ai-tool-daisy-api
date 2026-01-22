package com.example.ai.tool.analysis.ai_tool_daisy_api.exception;

/**
 * Exception thrown when JSON serialization or deserialization fails.
 * This provides a more specific exception type than generic RuntimeException
 * for JSON processing errors.
 */
public class JsonSerializationException extends RuntimeException {

    /**
     * Constructs a new JsonSerializationException with the specified detail message.
     *
     * @param message the detail message
     */
    public JsonSerializationException(String message) {
        super(message);
    }

    /**
     * Constructs a new JsonSerializationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public JsonSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
