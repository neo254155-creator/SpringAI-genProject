# Implementation log

Record each completed milestone here with its changes, verification, and remaining
limitations. Use a short, meaningful commit message for each milestone and push it
to the configured GitHub remote. Report a push as successful only after Git confirms
it; keep planned features separate from implemented features.

## Earlier milestone — Select a focus and request an interview question

Implemented on 2026-10-23.

The first flow lets the user select **Java → Spring Boot → intermediate** and
request an interview question.

### Changes, step by step

1. Added `InterviewSelection` with supported choices: Java; Core Java or Spring
   Boot; beginner, intermediate, or advanced. Unsupported or missing choices are
   rejected before calling the AI provider.
2. Added `GET /interview/options` to supply those choices to the frontend.
3. Added `POST /interview/questions`, accepting `track`, `topic`, and `level` as
   JSON. Its response includes those fields and the generated `question`.
4. Added `InterviewService.generateQuestion()` and the dynamic
   `src/main/resources/Prompt/InterviewQuestion` template. The system prompt asks
   for exactly one question without answers or hints. Question calls use
   temperature 0.5 and a 500-token output budget.
5. Added a homepage at `src/main/resources/static/index.html` and updated
   `coach.js` with selection fields, a question button, loading state, cancellation,
   timeout handling, and error messages. Generated content is displayed as text.
6. Added validation and provider-error handling: invalid selections or malformed
   JSON return 400; provider failures and empty questions return 502.
7. Added backend tests for options, prompt rendering, validation, empty output,
   and provider failures. Updated the stale `/chat` controller test and removed
   the live provider call from the application context test.
8. Updated `frontend/README.md` with startup instructions and an API example.

### Verification

- `./mvnw -o clean test -q` passed: 14 tests, no failures or errors.
- JavaScript syntax check passed using `node --input-type=module --check`.
- Node-based frontend request checks passed for successful output, provider
  errors, and empty responses using a stubbed `fetch`.
- `git diff --check` passed before committing.
- Live AI output and browser rendering have not been tested. The one-question
  requirement is a prompt instruction, not a programmatically enforced guarantee.

### Try it

Configure the existing AI provider, run `./mvnw spring-boot:run`, and open
`http://localhost:8080/`. Choose a topic and level, then click **Get interview
question**.

### Remaining work — not implemented

- Answer submission with correct points, missing points, explanation, and a score
  against a defined rubric.
- Next-question selection based on weaknesses.
- Stored sessions and a topic-progress dashboard.
- Résumé skill extraction and relevant interview questions.
- Uploaded notes with passage-backed explanations.
- Conversation memory within an interview.
- Controlled Java methods to retrieve scores and save study tasks.

Suggested next milestone: answer submission and rubric-based feedback.

Commit message for this milestone: `Add interview question flow`


## Revised build order — Step 1: Fix the foundation

Implemented on 2026-10-23. This follows the revised build order requested after the
question-selection milestone above.

### Changes, step by step

1. Replaced the hard-coded `ChatTemplate()` service contract with `chat(message)`.
   `GET /chat?q=...` returns text; new `POST /chat` accepts a JSON `message` and
   returns a JSON `content` field. Calling GET without `q` now returns 400.
2. Fixed classpath resource casing and loaded both prompt files from `Prompt/`.
   The user template now accepts the actual message instead of fixed Spring values.
   Chat calls use a 1000-token output budget.
3. Added service-level validation for blank messages and messages longer than
   8000 Java string characters. Tests cover the length boundary and Java code
   containing braces in prompt input.
4. Added explicit invalid-input and AI-provider exceptions. Centralized HTTP
   error handling into problem responses with safe descriptions. Application
   errors use 400, 502, or 500 as appropriate; Spring MVC retains protocol errors
   such as 405 and 415. Empty AI responses count as provider failures.
5. Applied the same provider boundary and error contract to the existing interview
   endpoint. Updated the frontend to read the problem response's `detail` field.
6. Aligned controller tests with GET and POST chat input. Added offline service
   tests using an in-memory ChatModel stub; no provider requests occur in the
   default suite.
7. Added `GroqLiveTest`, tagged `live`, excluded from normal tests. The
   `live-groq` Maven profile selects live tests only. The test requires a nonblank
   `GROQ_API_KEY` and otherwise skips before creating its Spring context.
8. Added root README instructions for input, error responses, environment setup,
   and separate offline/live test commands.

### Verification

- Offline suite: 34 tests passed with no failures or errors.
- JavaScript syntax and `git diff --check` passed.
- `env -u GROQ_API_KEY ./mvnw -o -Plive-groq test -q`: only the live test
  was selected, and it was skipped because credentials were absent (1 skipped,
  no failures or errors).
- No real Groq request or browser rendering verification was performed. Stub tests
  verify API behavior and prompt construction, not model answer quality.

### Remaining revised steps — not implemented by this milestone

2. Complete topic → question → answer → structured feedback flow.
3. PostgreSQL users, sessions, questions, answers, and feedback.
4. Document search with citations and an embedding model; pgvector is an option.
5. Extend the existing basic frontend with interview, history, and progress screens;
   add Docker and deployment.
6. Fixed-answer scoring evaluation and citation-support evaluation.

Commit message for this milestone: `Fix chat foundation`

## Configuration follow-up — Use the configured model

1. Fixed an unfinished method-chain dot in `ChatOptionsConfig`.
2. Made the shared ChatClient explicitly read `spring.ai.openai.chat.model` and
   pass it to `.model(model)`. The existing property defaults to
   `openai/gpt-oss-120b` and supports the `GROQ_MODEL` environment override.
3. A regression test found that per-request option builders replaced the shared
   client options. Removed those overrides from both services so configuration is
   applied consistently. Both now use temperature 0.2 and a 1000-token output
   budget from `ChatOptionsConfig`, replacing the earlier per-service budgets.
4. Added a test that inspects the actual prompts sent to a stub model by both
   services and checks the configured model and token budget.

A model name does not supply authentication: real calls still require
`GROQ_API_KEY`. No live provider call was made for this change.

Commit message: `Use shared model configuration`

Verification: `./mvnw -o test -q` passed all 35 offline tests, including the shared
model configuration regression test. `git diff --check` passed.
