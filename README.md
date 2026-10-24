# genProject

Java 21, Spring Boot 4.1.1, and Spring AI 2.0.1 application using Groq's
OpenAI-compatible API. See [implementation.md](implementation.md) for completed
milestones and pending work. See [PROJECT_REVIEW.md](PROJECT_REVIEW.md) for the
current code review and remaining roadmap.

## Run

The application now requires PostgreSQL. See [PERSISTENCE.md](PERSISTENCE.md) for
local `InterviewData` database setup, stored-session APIs, and progress queries.
The Windows setup script is `scripts/setup-local-postgres.ps1`; it creates the
database and runs migrations without requiring a Groq key.

Provide `GROQ_API_KEY` in your environment, then run:

```sh
./mvnw spring-boot:run
```

`GROQ_MODEL` optionally overrides the configured `openai/gpt-oss-120b` model.
`ChatOptionsConfig` reads that model property and sets shared defaults for both
chat and interview calls: temperature 0.2 and a 1000-token output budget.
The configured provider base URL is `https://api.groq.com/openai/v1`.
Open `http://localhost:8080/` for the existing question-selection screen.

## Chat input

The legacy hard-coded Spring demonstration has been replaced by user input.
`GET /chat` now requires the `q` parameter and returns text:

```sh
curl --get http://localhost:8080/chat --data-urlencode 'q=Explain dependency injection'
```

`POST /chat` accepts JSON and returns a JSON object containing `content`:

```sh
curl http://localhost:8080/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Explain dependency injection with a Java example"}'
```

Messages must be nonblank and at most 8000 Java string characters. Validation
also runs when the service is called directly. User content is substituted into
`Prompt/UserMessage`; Java braces in the input are preserved as data.
`Prompt/SystemMessage` supplies the coding-assistant instructions.

The existing interview APIs remain `GET /interview/options` and
`POST /interview/questions`. See [frontend/README.md](frontend/README.md).

## Structured interview feedback

After obtaining a question from `POST /interview/questions`, submit that question
and your answer to `POST /interview/feedback`:

```sh
curl http://localhost:8080/interview/feedback \
  -H 'Content-Type: application/json' \
  -d '{
    "track":"Java",
    "topic":"Spring Boot",
    "level":"intermediate",
    "question":"How does dependency injection work?",
    "answer":"Spring supplies dependencies to objects."
  }'
```

The question and answer are required (limits: 2000 and 8000 Java string characters).
The selection uses the same supported values as question generation. This endpoint is
stateless: the caller supplies the question; the server does not yet store or
verify that it was generated in a previous request.

The response is an `InterviewFeedback` Java record serialized to JSON:

```json
{
  "correctPoints": ["Identifies dependency injection"],
  "missingPoints": ["Explain constructor injection"],
  "explanation": "Spring supplies dependencies; constructor injection makes them explicit.",
  "rubric": {
    "correctness": {"score": 4, "reason": "Minor technical imprecision"},
    "completeness": {"score": 2, "reason": "One key point missing"},
    "clarity": {"score": 1, "reason": "Understandable but imprecise"}
  },
  "score": 7,
  "maxScore": 10
}
```

This is an illustrative test fixture, not a real model evaluation of the example.
Rubric version 1 defines correctness 0–5, completeness 0–3, and clarity 0–2.
The prompt includes scoring anchors and asks for a reason for every component.
Java validates required fields and score ranges, rejects fractional scores, and
calculates the total. Malformed or incomplete model feedback returns a safe 502;
invalid requests return 400. The shared model configuration also applies here.

Spring AI's `BeanOutputConverter<FeedbackEvaluation>` supplies the JSON schema in
the system prompt and converts the model response into Java records. This does
not guarantee scoring quality or resistance to instructions inside an answer;
real-model evaluation remains future work. The frontend currently shows questions
only; feedback is available through the API. The original question/feedback routes
are stateless. Use the new session APIs
in [PERSISTENCE.md](PERSISTENCE.md) to save questions, answers, and feedback.

## Error contract

Errors use `application/problem+json` with `type`, `title`, `status`, `detail`,
and `instance`. Application-defined errors also include `code`:

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | Blank/oversized message or unsupported interview selection |
| 502 | `AI_PROVIDER_ERROR` | Provider call failed or returned empty content |
| 404 | `NOT_FOUND` | Stored session or question does not exist |
| 409 | `SESSION_COMPLETED` | A completed session cannot accept new work |
| 503 | `DATABASE_UNAVAILABLE` | Could not connect to interview storage |
| 500 | `INTERNAL_ERROR` | Unexpected application failure |

Spring MVC supplies problem responses for malformed/missing JSON, missing query
parameters, incompatible Accept headers, unsupported media types, and unsupported
methods, preserving their 400, 406, 415, or 405 statuses. Those framework responses
need not contain `code`.
Provider exception text is not included in client responses. The frontend reads
`detail` to display errors.

## Test modes

Default tests use in-memory model stubs for AI calls. Application-context MVC
tests load the actual application with a primary stub ChatModel, a dummy key, and
a fixed test model name. Context tests use a test-only H2 database with Flyway
migrations; they make no provider requests:

```sh
./mvnw clean test
```

Tests tagged `live` or `postgres` are excluded by default. The optional
`postgres-test` profile runs persistence tests against disposable PostgreSQL with
Testcontainers (Docker required). To select only the live Groq smoke
test, provide `GROQ_API_KEY` and run:

```sh
./mvnw -Plive-groq test
```

Without a nonblank `GROQ_API_KEY`, the live test is skipped before loading its
Spring context. With credentials it makes a real request and may incur provider
charges. The smoke test only checks for a nonempty response; it does not assess
technical correctness or interview quality. It has not yet been verified against
live Groq in this environment.
