# Interview persistence: PostgreSQL + JPA

The session APIs store successful interviews using Spring Data JPA. PostgreSQL is
the runtime database. Flyway applies versioned SQL migrations; Hibernate validates
the resulting schema rather than creating or changing tables automatically.

## Local database setup

The requested database name is now **InterviewData** (case-sensitive). PostgreSQL
18 was found installed on Windows at `C:\Program Files\PostgreSQL\18`.
This workspace cannot execute Windows programs or open database sockets, so an
actual `InterviewData` database has **not** been created or verified by the agent.

From the project folder in Windows PowerShell, run:

```powershell
.\scripts\setup-local-postgres.ps1
```

The script prompts securely for the `postgres` administrator password and the
`genproject` application-login password. Choose an app password if that role is
new; supply its current password if it already exists. It creates a new login
only when needed, creates the quoted database `"InterviewData"` owned by that
login, checks connectivity, runs the project Flyway migrations, and verifies all
six tables through JDBC. It requires Java 21+ on PATH and the Maven wrapper's
runtime dependencies. This setup does not require or call Groq.

It writes `.local/database.properties` with machine-local connection settings.
That directory is ignored by Git and imported optionally by Spring Boot. The
`DB_URL`, `DB_USER`, and `DB_PASSWORD` environment variables can override these
local settings. No credentials are printed or embedded in the tracked files.
An existing database owned by someone else is not modified; an existing login's
password is not reset. Migration failures stop the script with an error.

Defaults connect to `localhost:5432`. Use `-ServerHost`, `-Port`, `-AdminUser`,
`-AppUser`, or `-PsqlPath` for a different local installation. If the Java app runs
in WSL and Windows PostgreSQL is not reachable through WSL localhost, provide the
reachable Windows address with `-ApplicationHost` and ensure PostgreSQL accepts
that connection. Windows connectivity does not prove WSL connectivity.

### Optional Docker alternative

If Docker is installed:

```sh
docker compose up -d postgres
```

The supplied Compose file runs PostgreSQL 17 on localhost:5432 and keeps data in a
named volume. Its development defaults are database `InterviewData`, user
`genproject`, and password `genproject`. The application uses the same defaults.
`DB_USER` and `DB_PASSWORD` override the credentials for both Compose and the app.
For an existing database, set:

```sh
export DB_URL=jdbc:postgresql://localhost:5432/InterviewData
export DB_USER=genproject
# Set DB_PASSWORD for that database in your environment.
./mvnw spring-boot:run
```

The database itself must exist; the PowerShell script or a fresh Compose volume
creates it. Flyway creates tables within it. Changing POSTGRES_DB in Compose does
not create a new database inside an already-initialized volume; do not delete a
volume containing data to rename a database. Groq credentials
are still needed to start the normal application and make real AI calls, as
described in the root README. Database and API-key setup are separate concerns.
This milestone did not run Docker or a real PostgreSQL server in the workspace.

## Persisted workflow

All paths below start with `/interview`. History lists accept zero-based `page`
and `size` (default 20, maximum 100), and return `content`, `page`, `size`,
`totalElements`, and `totalPages`. Lists are newest first, with ID as a tie-breaker.

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/sessions` | Create an ACTIVE session from track/topic/level; returns 201 and a Location header |
| GET | `/sessions` | List stored sessions |
| GET | `/sessions/{id}` | Read session details |
| POST | `/sessions/{id}/questions` | Generate and save a question for the session's stored selection; returns 201 |
| GET | `/sessions/{id}/questions` | List that session's questions |
| POST | `/questions/{id}/answers` | Accept JSON `answer`, evaluate the stored question, and save answer plus feedback; returns 201 |
| GET | `/questions/{id}/answers` | List attempts and feedback for a stored question |
| POST | `/sessions/{id}/complete` | Mark a session completed; repeating this preserves its completion time |
| GET | `/progress` | Aggregate saved scores by track, topic, and difficulty |

Start a session:

```sh
curl http://localhost:8080/interview/sessions \
  -H 'Content-Type: application/json' \
  -d '{"track":"Java","topic":"Spring Boot","level":"intermediate"}'
```

Use the returned session `id` to request a question:

```sh
curl -X POST http://localhost:8080/interview/sessions/SESSION_ID/questions
```

Use the returned question `id` to submit an answer:

```sh
curl http://localhost:8080/interview/questions/QUESTION_ID/answers \
  -H 'Content-Type: application/json' \
  -d '{"answer":"Spring supplies dependencies through the container."}'
```

`SESSION_ID` and `QUESTION_ID` above are placeholders for returned UUIDs.
The answer response includes its ID, question ID, answer text, timestamp,
`rubricVersion` (`java-interview-v1`), and the typed `feedback` object. Read it again
through the question's answers endpoint to retrieve the stored result without an
AI call. Submit only `answer`: the question, topic, and difficulty come from the
database, not from caller-supplied evaluation context.

## Storage and consistency

Migration: `src/main/resources/db/migration/V1__create_interview_history.sql`.

- `interview_sessions`: selection, ACTIVE/COMPLETED status, timestamps, version.
- `interview_questions`: generated text, session foreign key, creation time.
- `interview_answers`: submitted text, question foreign key, submission time.
- `interview_feedback`: explanation, component scores/reasons, total, maximum,
  rubric version, and a unique answer foreign key.
- `feedback_correct_points` and `feedback_missing_points`: ordered feedback lists.

Foreign keys, uniqueness, and score checks protect the stored relationships and
rubric totals. Timestamps use UTC instants at microsecond precision. Question text
is limited to 2000 characters and answers to 8000, matching evaluation limits.

AI calls happen outside database transactions. A successful evaluation is then
saved as an answer and feedback in one transaction. Provider failure or invalid
output saves neither; the caller can retry. A database failure rolls back both.
Session state is rechecked under a row lock when saving, so completion during an
AI call prevents a late write. Completing a session also prevents further question
or answer requests before they call AI. History remains readable after completion.
Each accepted answer submission creates a new attempt; automatic retry deduplication
is not implemented.

Missing sessions/questions return 404, completed-session writes return 409,
invalid input returns 400, provider failures return 502, and database connection
failures return a safe 503 problem response.

## Progress meaning and current scope

Progress returns attempts, distinct questions answered, average score, best score,
and last practice time, grouped by track/topic/level. Average/best scores are out
of 10. Repeated attempts count separately; empty sessions do not inflate the data.
This is a practice summary, not an inferred mastery percentage.

This is currently a single-learner workspace: there is no login, user table, or
per-user access control. Session history and progress cover the database as a whole.
The existing question-only frontend and original stateless `/questions` and
`/feedback` endpoints remain available; those stateless routes do not save history.
Use the session routes above for persistence. A history/progress UI is future work.

## Verification

```sh
# Offline suite: local AI stubs + H2 in PostgreSQL compatibility mode.
# Runs Flyway's migration and Hibernate schema validation.
./mvnw clean test

# Same persistence contract on disposable PostgreSQL 17, using local AI stubs.
# Requires Docker; tests skip when Docker is unavailable.
./mvnw -Ppostgres-test test
```

The default suite excludes `live` and `postgres` tags. The `postgres-test` profile
selects only PostgreSQL tests; `live-groq` still selects only the Groq smoke test.
H2 tests are useful for JPA mappings, application transactions, and queries, but
are not proof that the PostgreSQL container test passed. See `implementation.md`
for commands actually run and their results.
