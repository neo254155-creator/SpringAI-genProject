# Deploy to Vercel

Vercel detects `Dockerfile.vercel` at the project root. Set the Vercel Root
Directory to the folder containing this file and `pom.xml`.

The image builds the Spring Boot JAR with Java 21 and the Maven wrapper, then
runs it with a Java 21 runtime as a non-root user. Image builds skip tests;
run `./mvnw test` separately before deploying. The existing static frontend
is included in the JAR and uses the same origin as the API.

Set these in the Vercel project's environment settings before deploying:

| Variable | Value |
| --- | --- |
| `PORT` | `8080` (match the application's listening port) |
| `SPRING_PROFILES_ACTIVE` | `production` (requires HTTPS; JDBC sessions and Secure cookies) |
| `DB_URL` | Your hosted PostgreSQL JDBC URL, e.g. `jdbc:postgresql://YOUR_HOST/InterviewData?sslmode=require`; use the actual hosted database name |
| `DB_USER` | Hosted PostgreSQL username |
| `DB_PASSWORD` | Hosted PostgreSQL password |
| `GROQ_API_KEY` | Your Groq API key |
| `GROQ_MODEL` | Optional; defaults to `openai/gpt-oss-120b` |

For optional document search, also set `DOCUMENT_EMBEDDINGS_ENABLED=true`,
`DOCUMENT_EMBEDDINGS_PROVIDER=openai-compatible`, `DOCUMENT_EMBEDDINGS_URL`,
`DOCUMENT_EMBEDDINGS_MODEL`, and `DOCUMENT_EMBEDDINGS_API_KEY`. The URL must be
your provider's HTTPS API base URL. See DOCUMENTS.md for the required API format.
The interview question/answer/feedback screen does not require embeddings.

Use a reachable hosted PostgreSQL database, such as Neon. `localhost` inside
Vercel refers to the container, not your computer. This Dockerfile does not
create or move a database. Flyway applies pending migrations on app startup
using the supplied database credentials. Local `.local/database.properties`
and other files outside the build allowlist are excluded from the image context.

Deploy through your connected Vercel Git repository or run `vercel deploy`
from this directory with an authenticated Vercel CLI. These instructions do
not mean a deployment has been performed or verified.

Publishing is deferred until the UI work is finished. Before publishing, create
your first account locally against the hosted database and confirm legacy ownership.
Remote/forwarded first-account setup is deliberately blocked. The production
profile requires explicit DB settings, uses at most three database connections
per instance, and shares sign-in/CSRF state using V8's Spring Session JDBC tables.
Keep the API and frontend on one HTTPS origin. Do not disable Secure cookies online.

Configure a shared platform rate limit for `POST /auth/login` and
`POST /auth/register` before opening public registration; the in-process limiter
does not share counters between instances. Verify login/logout, account isolation
and saved progress on a preview deployment before publishing. Runtime/provider
limits still need checking on the chosen hosting account.

The application JAR and local/production browser journeys were verified. Docker
image build/run remains unverified because no Docker CLI/daemon is available here.
Authenticated database sessions passed the disposable H2 restart check. Neon V8,
production cookie flags, anonymous CSRF-session restart and logout cleanup also
passed. Authenticated hosted-session behavior still needs a preview check.

For a local image build with Docker installed:

```sh
docker build -f Dockerfile.vercel -t genproject .
```

Official platform documentation:
https://vercel.com/kb/guide/does-vercel-support-docker-deployments
https://docs.spring.io/spring-session/reference/configuration/jdbc.html
