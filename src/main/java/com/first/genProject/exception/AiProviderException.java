package com.first.genProject.exception;

/** Public messages and log categories never include provider bodies or user input. */
public class AiProviderException extends RuntimeException {
    public enum Reason { PROVIDER_FAILURE, RATE_LIMIT, AUTHENTICATION, INVALID_RESPONSE, TIMEOUT, QUOTA_LIMIT, BUSY, STORAGE_UNAVAILABLE }
    private final Reason reason;
    public AiProviderException(Throwable cause) { this(classify(cause), cause); }
    public AiProviderException() { this(Reason.PROVIDER_FAILURE, null); }
    public AiProviderException(Reason reason, Throwable cause) {
        super(switch (reason) {
            case QUOTA_LIMIT -> "Your account or this site has reached its daily AI allowance. Try again after midnight UTC.";
            case BUSY -> "Another AI request is running for your account. Wait before trying again.";
            case STORAGE_UNAVAILABLE -> "AI quota protection is temporarily unavailable. Please try later.";
            case RATE_LIMIT -> "The AI provider's usage limit was reached. Wait before trying again.";
            case AUTHENTICATION -> "The AI provider rejected the server credentials. The server configuration needs checking.";
            case INVALID_RESPONSE -> "The AI returned an invalid result. Your input has been kept; please try again.";
            case TIMEOUT -> "The AI provider did not respond in time. Please try again later.";
            default -> "The AI provider could not complete the request. Please try again.";
        }, cause);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
    private static Reason classify(Throwable cause) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = cause; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof AiProviderException ai) return ai.reason();
            int status = current instanceof com.openai.errors.OpenAIServiceException sdk ? sdk.statusCode()
                    : current instanceof org.springframework.web.client.RestClientResponseException http ? http.getStatusCode().value() : 0;
            if (status == 429) return Reason.RATE_LIMIT;
            if (status == 401 || status == 403) return Reason.AUTHENTICATION;
            if (current instanceof java.net.SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException)
                return Reason.TIMEOUT;
        }
        return Reason.PROVIDER_FAILURE;
    }
}
