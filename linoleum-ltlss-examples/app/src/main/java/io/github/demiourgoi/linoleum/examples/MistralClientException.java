package io.github.demiourgoi.linoleum.examples;

/**
 * Runtime exception for MistralClient errors
 */
public class MistralClientException extends RuntimeException {

    /**
     * Constructor with message
     * @param message The error message
     */
    public MistralClientException(String message) {
        super(message);
    }

    /**
     * Constructor with message and cause
     * @param message The error message
     * @param cause The underlying cause
     */
    public MistralClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor for HTTP status errors
     * @param statusCode The HTTP status code
     * @param statusMessage The HTTP status message
     */
    public MistralClientException(int statusCode, String statusMessage) {
        super("Mistral API returned status " + statusCode + ": " + statusMessage);
    }
}