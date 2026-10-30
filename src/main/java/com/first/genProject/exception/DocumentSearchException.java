package com.first.genProject.exception;

public class DocumentSearchException extends RuntimeException {
    public enum Reason { DISABLED, PROVIDER_FAILURE, INDEX_NOT_READY }
    private final Reason reason;

    public DocumentSearchException(Reason reason) {
        super(switch (reason) {
            case DISABLED -> "Configure and enable an embedding provider before indexing or searching documents.";
            case PROVIDER_FAILURE -> "The embedding provider could not complete the request. Please try again.";
            case INDEX_NOT_READY -> "Index this document with the currently configured embedding model before searching.";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
