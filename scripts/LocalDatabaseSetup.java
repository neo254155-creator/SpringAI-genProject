import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.flywaydb.core.Flyway;

/** Run by setup-local-postgres.ps1; does not start Spring or call an AI provider. */
class LocalDatabaseSetup {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(project.resolve(".local/database.properties"), StandardCharsets.UTF_8)) {
            settings.load(reader);
        }
        String url = settings.getProperty("local.database.url");
        String user = settings.getProperty("local.database.user");
        String password = settings.getProperty("local.database.password");
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database()")) {
            if (!result.next() || !"InterviewData".equals(result.getString(1))) {
                throw new IllegalStateException("Refusing to migrate a database other than InterviewData");
            }
        }
        Flyway.configure().dataSource(url, user, password)
                .locations("filesystem:" + project.resolve("src/main/resources/db/migration"))
                .load().migrate();

        Set<String> required = Set.of("interview_sessions", "interview_questions", "interview_answers",
                "interview_feedback", "feedback_correct_points", "feedback_missing_points");
        Set<String> found = new HashSet<>();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rows.next()) found.add(rows.getString(1));
        }
        if (!found.containsAll(required)) {
            throw new IllegalStateException("Expected interview tables are missing after migration");
        }
        System.out.println("Verified InterviewData: JDBC connection, Flyway migration, and all six interview tables.");
    }
}
