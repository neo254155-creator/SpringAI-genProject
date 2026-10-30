import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.config.DocumentEmbeddingConfig;

/** Read-only database checks plus one small request per configured AI provider. */
class VerifyLiveConnections {
    private final StandardEnvironment settings = new StandardEnvironment();

    public static void main(String[] args) {
        var checks = new VerifyLiveConnections();
        try {
            ConfigDataEnvironmentPostProcessor.applyTo(checks.settings);
        } catch (RuntimeException exception) {
            System.out.println("FAIL configuration: could not load application settings; no secret values displayed.");
            System.exit(1);
        }
        int passed = 0;
        if (checks.run("PostgreSQL", checks::database)) passed++;
        if (checks.run("Groq", checks::groq)) passed++;
        if (checks.run("Embeddings", checks::embeddings)) passed++;
        System.out.println("Verified " + passed + "/3 services. Missing or disabled settings are not passes.");
        System.exit(passed == 3 ? 0 : 1);
    }

    private boolean run(String name, Check check) {
        try {
            String detail = check.run();
            System.out.println("PASS " + name + ": " + detail);
            return true;
        } catch (NotConfigured exception) {
            System.out.println("NOT_CONFIGURED " + name + ": " + exception.getMessage());
        } catch (Exception exception) {
            System.out.println("FAIL " + name + ": " + safeFailure(exception));
        }
        return false;
    }

    private String database() throws Exception {
        String url = required("spring.datasource.url", "Set the database URL.");
        if (!url.startsWith("jdbc:postgresql:")) throw new NotConfigured("A PostgreSQL JDBC URL is required; test databases do not count.");
        var properties = new Properties();
        properties.setProperty("user", required("spring.datasource.username", "Set the database username."));
        properties.setProperty("password", settings.getProperty("spring.datasource.password", ""));
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        DriverManager.setLoginTimeout(5);
        try (var connection = DriverManager.getConnection(url, properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (var rows = statement.executeQuery("SELECT current_database()")) {
                    if (!rows.next()) throw new IllegalStateException();
                }
                Set<String> found = new HashSet<>();
                try (var rows = statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()")) {
                    while (rows.next()) found.add(rows.getString(1));
                }
                var required = Set.of("interview_sessions", "interview_questions", "interview_answers", "interview_feedback",
                        "feedback_correct_points", "feedback_missing_points", "study_documents", "study_passages",
                        "document_search_indexes", "resume_profiles", "flyway_schema_history");
                if (!found.containsAll(required)) throw new SchemaProblem("Expected application tables or Flyway history are missing; no migrations were run.");
                Set<String> versions = new HashSet<>();
                try (var rows = statement.executeQuery("SELECT version, success FROM flyway_schema_history")) {
                    while (rows.next()) {
                        if (!rows.getBoolean(2)) throw new SchemaProblem("Flyway history contains a failed migration.");
                        versions.add(rows.getString(1));
                    }
                }
                if (!versions.containsAll(Set.of("1", "2", "3", "4", "5"))) {
                    throw new SchemaProblem("Expected successful Flyway versions V1 through V5; no migrations were run.");
                }
                Set<String> columns = new HashSet<>();
                try (var rows = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'interview_questions'")) {
                    while (rows.next()) columns.add(rows.getString(1));
                }
                if (!columns.containsAll(Set.of("focus_criterion", "focus_reason", "resume_skill"))) throw new SchemaProblem("Practice-focus or V5 resume skill columns are missing.");
                columns.clear();
                try (var rows = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'interview_sessions'")) {
                    while (rows.next()) columns.add(rows.getString(1));
                }
                if (!columns.containsAll(Set.of("resume_id", "resume_context_json"))) throw new SchemaProblem("V5 resume session columns are missing.");
                // Confirm the configured login can actually read application tables, not just see their names.
                for (String table : required) {
                    try (var rows = statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) { rows.next(); }
                }
            } finally { connection.rollback(); }
        }
        return "JDBC connection, read access, expected tables, V1-V5 history, and practice-focus columns checked without writes.";
    }

    private String groq() throws Exception {
        String key = required("spring.ai.openai.api-key", "Set GROQ_API_KEY in the environment that runs this check.");
        String model = required("spring.ai.openai.chat.model", "Set the chat model.");
        String baseUrl = required("spring.ai.openai.base-url", "Set the chat provider URL.").replaceAll("/+$", "");
        var response = client().baseUrl(baseUrl).build().post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON).headers(headers -> headers.setBearerAuth(key))
                .body(Map.of("model", model, "messages", List.of(Map.of("role", "user", "content", "Reply with OK only.")),
                        "max_tokens", 128, "temperature", 0))
                .retrieve().body(String.class);
        var body = JsonMapper.builder().build().readTree(response);
        var text = body.path("choices").path(0).path("message").path("content");
        if (!text.isString() || text.asString().isBlank()) throw new SchemaProblem("Provider responded but no nonempty chat message was returned.");
        return "the configured chat model returned a nonempty message (response text hidden).";
    }

    private String embeddings() throws Exception {
        if (!settings.getProperty("document-search.embedding.enabled", Boolean.class, false)) {
            throw new NotConfigured("Embeddings are disabled; configure the provider and enable them to verify.");
        }
        String provider = settings.getProperty("document-search.embedding.provider", "openai-compatible");
        String url = required("document-search.embedding.base-url", "Set the embedding provider URL.");
        String model = required("document-search.embedding.model", "Set the embedding model.");
        String key = "ollama".equals(provider) ? "" : required("document-search.embedding.api-key", "Set the embedding provider API key.");
        var adapter = new DocumentEmbeddingConfig().passageEmbeddingClient(true, provider, url, model, key);
        var vectors = adapter.embed(List.of("A Java interface defines a contract."));
        return "the configured adapter returned a validated embedding with " + vectors.getFirst().length + " dimensions.";
    }

    private String required(String property, String hint) throws NotConfigured {
        final String value;
        try { value = settings.getProperty(property); }
        catch (IllegalArgumentException exception) { throw new NotConfigured(hint); }
        if (value == null || value.isBlank()) throw new NotConfigured(hint);
        return value;
    }

    private static RestClient.Builder client() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory);
    }

    private String safeFailure(Exception exception) {
        if (exception instanceof SchemaProblem problem) return problem.getMessage();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException) return "DNS lookup failed.";
            if (cause instanceof ConnectException) return "Connection could not be established; check service availability and network access.";
            if (cause instanceof SocketException) return "Network/socket access failed; the execution environment may block connections.";
            if (cause instanceof RestClientResponseException response) return "Provider returned HTTP " + response.getStatusCode().value() + "; check key, model, endpoint, and provider limits.";
            if (cause instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("28")) return "Database authentication failed.";
        }
        return "The check failed. Check configuration, service availability, and network access; private error details were suppressed.";
    }

    @FunctionalInterface private interface Check { String run() throws Exception; }
    private static class NotConfigured extends Exception { NotConfigured(String message) { super(message); } }
    private static class SchemaProblem extends Exception { SchemaProblem(String message) { super(message); } }
}
