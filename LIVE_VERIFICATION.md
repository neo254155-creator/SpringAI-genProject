# Verify real database and AI connections

From the project directory, run:

```sh
python3 scripts/verify-live-connections.py
```

On Windows, use `python scripts/verify-live-connections.py` if Python is named
`python`. Java 21 and the Maven wrapper's prerequisites must be available.

The launcher compiles the current application and builds a runtime classpath.
It runs a Java source helper without starting the server, JPA, or Flyway.
Spring's configuration loader reads the current application properties,
environment overrides, active configuration profiles, and optional
`.local/database.properties` import. Run it with the same environment as your app.
Application command-line arguments are not forwarded by this launcher.

| Check | What it verifies |
| --- | --- |
| PostgreSQL | Real JDBC connection, read access to the nine application tables and Flyway history, successful V1–V4 history entries, and V4 focus columns. |
| Groq | One small chat request using the configured provider URL, key, and model; requires a nonempty returned message. |
| Embeddings | One short text sent through the configured hosted/Ollama adapter; requires a validated numerical vector. |

The database connection is read-only; the helper uses SELECT statements and
rolls back. It does not create a database, run migrations, insert records, or
change credentials. AI checks use real provider requests and may consume credits.
Keys, passwords, URLs, provider response text, and raw exception messages are not
printed. No JSON report containing credentials is written.

Before running, configure `GROQ_API_KEY` and your PostgreSQL connection settings.
The app's database settings may be literals or references; the helper follows
the current resolved configuration. For hosted embeddings, supply a compatible
provider URL, model, and separate API key in the existing embedding properties,
then enable them. If you removed the environment placeholders from those
properties, setting the old environment variable names alone will not fill blank
property values; restore the references or supply Spring property overrides.
See DOCUMENTS.md for settings. Do not paste keys into chat or commit them.

Output is `PASS`, `FAIL`, or `NOT_CONFIGURED` per service, followed by a verified
count. Exit code 0 requires all three services to pass; code 1 means at least one
remains unverified. Disabled embeddings do not count as a pass. Checks continue
after one service fails, so the other services can still be reported.

This is a connection/schema smoke check, not full API or browser verification.
It does not check migration checksums, every column/index/constraint, scoring
quality, retrieval quality, or production readiness. The existing offline suite
still uses H2 and provider stubs; those results do not prove live connectivity.

## Observed result in the agent environment — 2026-10-26

- PostgreSQL: socket/network access failed in this restricted environment.
- Groq: `GROQ_API_KEY` was not set in this process.
- Embeddings: disabled; URL/model/key properties were blank.
- Result: **0/3 services verified**. No database migration or AI request ran.

Run the command in your own terminal after configuring the services. A missing
variable in the agent process does not prove it is missing in another terminal,
your IDE, or your hosting environment.
