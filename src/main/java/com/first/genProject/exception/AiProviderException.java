package com.first.genProject.exception;

public class AiProviderException extends RuntimeException {
    public AiProviderException(Throwable cause) {
        super("The AI provider could not complete the request. Please try again.", cause);
    }

    public AiProviderException() {
        this(null);
    }
}
