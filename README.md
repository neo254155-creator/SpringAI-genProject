# genProject

Java 21, Spring Boot 4.1.1, and Spring AI 2.0.1 application using Groq's
OpenAI-compatible API. See [implementation.md](implementation.md) for completed
milestones and pending work.

## Run

Provide `GROQ_API_KEY` in your environment, then run:

```sh
./mvnw spring-boot:run
```

`GROQ_MODEL` optionally overrides the configured `openai/gpt-oss-120b` model.
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

## Error contract

Errors use `application/problem+json` with `type`, `title`, `status`, `detail`,
and `instance`. Application-defined errors also include `code`:

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | Blank/oversized message or unsupported interview selection |
| 502 | `AI_PROVIDER_ERROR` | Provider call failed or returned empty content |
| 500 | `INTERNAL_ERROR` | Unexpected application failure |

Spring MVC supplies problem responses for malformed/missing JSON, missing query
parameters, unsupported media types, and unsupported methods, preserving their
400, 415, or 405 statuses. Those framework responses need not contain `code`.
Provider exception text is not included in client responses. The frontend reads
`detail` to display errors.

## Test modes

Default tests use in-memory model stubs for AI calls; the application-context test
uses a dummy key and makes no provider request:

```sh
./mvnw clean test
```

Tests tagged `live` are excluded by default. To select only the live Groq smoke
test, provide `GROQ_API_KEY` and run:

```sh
./mvnw -Plive-groq test
```

Without a nonblank `GROQ_API_KEY`, the live test is skipped before loading its
Spring context. With credentials it makes a real request and may incur provider
charges. The smoke test only checks for a nonempty response; it does not assess
technical correctness or interview quality. It has not yet been verified against
live Groq in this environment.
