# Deploy to Vercel

Live: [Intervie](https://intervie-genproject.vercel.app), project `intervie-genproject`
in workspace `neo254155-4565`. Verified production deployment:
`dpl_FWDFieEZbYmLkJuC4EVkiytT49sL`, commit `a91c69f` (2026-11-05).
Main-branch pushes deploy automatically. The primary URL is public; preview and
unique deployment URLs keep Vercel Authentication. App sign-in protects user data.

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
| `SPRING_MAIN_LAZY_INITIALIZATION` | `true` (reduces cold-start work) |
| `SPRING_DATA_JPA_REPOSITORIES_BOOTSTRAP_MODE` | `lazy` |
| `JAVA_TOOL_OPTIONS` | `-XX:+TieredCompilation -XX:TieredStopAtLevel=1` (faster JVM startup) |
| `SPRING_FLYWAY_ENABLED` | `false` on Vercel only; run reviewed migrations once before release as below |
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
create or move a database. Local startup still applies Flyway migrations. On Vercel,
run migrations once before release instead of on every short-lived instance:
start the reviewed version locally with the same Neon settings and Flyway enabled,
then run the database-only connection check before deploying. V1–V8 are already
applied. Keep JPA schema validation enabled; do not bypass schema errors.
Local `.local/database.properties`
and other files outside the build allowlist are excluded from the image context.

Deploy through your connected Vercel Git repository or run `vercel deploy`
from this directory with an authenticated Vercel CLI. These instructions do
not mean a deployment has been performed or verified.

Deployment is now requested before UI improvements. Before publishing, create
your first account locally against the hosted database and confirm legacy ownership.
Remote/forwarded first-account setup is deliberately blocked. The production
profile requires explicit DB settings, uses at most three database connections
per instance, and shares sign-in/CSRF state using V8's Spring Session JDBC tables.
Keep the API and frontend on one HTTPS origin. Do not disable Secure cookies online.

The app's authentication throttle is active. Shared platform-wide limits are not
configured by this deployment; the in-process limiter does not share counters
between instances. Plan additional limits before a larger public rollout.
Hosted registration/login, cookie flags, account isolation, reload and logout
passed with disposable accounts, which were removed. No AI-generation request ran;
hosted Groq/Gemini generation remains untested to preserve the free-tier quota.

The application JAR and local/production browser journeys were verified. Vercel
successfully built and ran the Docker image; no local Docker installation was needed.
Authenticated database sessions passed the disposable H2 restart check. Neon V8,
production cookie flags, anonymous CSRF-session restart and logout cleanup also
passed. Hosted authenticated requests, reload, history isolation and logout passed.

For a local image build with Docker installed:

```sh
docker build -f Dockerfile.vercel -t genproject .
```

Official platform documentation:
https://vercel.com/kb/guide/does-vercel-support-docker-deployments
https://docs.spring.io/spring-session/reference/configuration/jdbc.html

## AI quotas and bounded inputs (V9)

Run reviewed V9 before releasing this version; Vercel keeps startup Flyway disabled. V9 was applied to Neon on 2026-11-06.

Optional environment overrides: `AI_ACCOUNT_DAILY_REQUESTS` (30), `AI_GLOBAL_DAILY_REQUESTS` (200), `AI_ACCOUNT_DAILY_CHARACTERS` (120000), `AI_GLOBAL_DAILY_CHARACTERS` (800000). These are application safeguards, not the provider’s remaining balance or token meter. Combined Groq/Gemini attempts, including failures, count; UTC midnight resets the daily counters. One AI call per account runs at a time across instances. A crashed process leaves a conservative 15-minute lease. Successful retries replayed from storage do not consume another attempt.

JSON bodies are capped at 128 KiB; chat prompts at 20000 characters. Existing field limits remain (résumé 24000, answers/chat 8000, queries 2000). Long résumés use locally selected source lines up to 12000 characters; omitted details may require a shorter résumé. Follow-up context and note evidence are bounded; submitted answers stay intact. Embeddings use at most 16 texts/20000 characters per call. Chat timeout is 90 seconds, output cap 1000 tokens, SDK retries remain disabled.

## Public-file caching

Successful GET/HEAD responses for `/`, `/index.html`, `/login.html`, `/privacy.html`, and existing public JS/CSS/SVG/ICO files use Vercel-only cache TTLs of one hour (HTML) or one day (assets), plus stale-while-revalidate. Stable browser URLs revalidate instead of retaining old deploy assets. Cookie-setting/error responses, APIs, configuration, private HTML, Authorization and Range requests are excluded. Cache misses/private requests can still incur a Java cold start. Connection/session lookups now allow 45 seconds, without automatic mutation retries.

Live baseline before this change: homepage 14.04s, CSS 0.88s, configuration 0.72s; all returned no-store and MISS. On release, verify repeated public requests return X-Vercel-Cache HIT and measure timings; do not assume header tests prove edge hits. References: https://vercel.com/docs/caching/cache-control-headers and https://vercel.com/docs/caching/cdn-cache.
