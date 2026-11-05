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
The frontend uses saved-session routes. `/history.html` shows previous sessions,
questions, answer attempts, rubric feedback, and topic progress.
`GET /interview/sessions/{id}/resume` returns session metadata, the latest question,
its latest saved answer/feedback (or null), and the count of distinct questions
answered. It reads saved data without calling AI or changing records. Completed
sessions can be opened for review. No schema change is needed.
The original stateless `/questions` and `/feedback` endpoints remain available;
those routes do not save history.

Stored question responses include optional `focus` with `criterion` and `reason`.
V4 adds nullable focus columns to `interview_questions`; older rows have no focus.
Next questions use the latest saved session feedback and avoid the five most
recent exact question texts. See ADAPTIVE_INTERVIEWS.md for selection rules.

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

## Safe retries

V6 adds `request_replays`. The browser sends a UUID `Idempotency-Key` with POSTs
for résumé upload/paste, session creation, questions, answers, completion, and
study-note upload/index/search/answers. API callers can send the same header;
calls without it retain the original behavior.

The same key and request replay the stored status, JSON and Location without
rerunning the action. Changed input or a running/unconfirmed outcome returns
409. Multipart fingerprints use file content and fields rather than the random
boundary. Known validation/provider failures replay their error; after receiving
that failure, an explicit browser retry uses a new key. Network loss retains the
key. Unknown outcomes stay blocked, including after a crash; they require checking
saved progress rather than automatically taking over the request.

Browser pending keys and input hashes survive tab reloads when session storage
is available, with an in-memory fallback. Raw files, résumé text and answers are
not put in retry storage. Successful response validation releases the browser key
so a later deliberate attempt can be new. A saved-session GET can reconcile a
completed question key without starting AI. Web Crypto requires HTTPS or localhost.

Database records contain request/key hashes and bounded response copies, which
can include saved answers, skill excerpts and generated note answers. They are
retained without automatic expiry; future retention cleanup must preserve the
protection for unresolved requests. These rows share the current application
workspace. Database claims are short autocommit operations; AI runs outside them.
The OpenAI/Groq SDK's hidden retries are disabled with `max-retries=0` settings.

## Accounts and ownership

Start the updated server, open `http://localhost:8080/login.html`, and create your
first account directly from the server's localhost address. As requested, that
account receives the existing unowned résumés, sessions/history/progress and notes
once. V7 adds accounts and owner columns; the transfer happens atomically during
first registration, not during migration. Remote/forwarded first registrations
are rejected. Later accounts start empty and cannot claim old rows.

Usernames are case-insensitive, 3–40 ASCII letters/digits/dots/underscores/hyphens;
passwords require 12 characters and at most 72 UTF-8 bytes and use BCrypt hashes.
No new API key or external authentication provider is required.

| API | Behavior |
| --- | --- |
| `GET /auth/csrf` | Public session CSRF token and header name. |
| `POST /auth/register` | Create an account; does not itself sign in. |
| `POST /auth/login` | Sign in and rotate the session ID/CSRF token. |
| `GET /auth/me` | Current account ID and username. |
| `POST /auth/logout` | Invalidate the session and delete its cookie. |

The browser registers then signs in, uses an HttpOnly/SameSite=Lax session cookie,
and sends a CSRF header with mutations and the billable legacy GET/HEAD `/chat`.
API clients must keep the cookie, fetch `/auth/csrf`, send its named header, and
fetch a fresh token after login/logout. Account sign-in and registration share a
per-IP limit of 30 attempts per five minutes, held in bounded per-process memory.

Lists/progress and all ID-based reads/writes enforce the owner, before AI calls.
Foreign resource IDs return 404. Retry results are scoped by the authenticated
account ID; anonymous shared-key results are not replayed after this upgrade.
The main résumé journey follows sign-in without other workflow changes.

Sessions expire after 30 minutes idle and are memory-local, so restarts require
sign-in again. Use HTTPS and secure session-cookie configuration for deployment;
multiple instances require shared session/rate-limit storage. The frontend uses
the same origin as its API; cross-origin credentialed CORS is not configured.
Password reset, MFA, and self-service account deletion/export remain future work.
V7 and bootstrap/ownership tests passed on H2; no live Neon account transfer ran.

Session persistence and CSRF handling follow Spring Security's
[session guidance](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
and [CSRF guidance](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
