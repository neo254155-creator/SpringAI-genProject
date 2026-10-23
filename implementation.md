# Implementation log

Record each completed milestone here with its changes, verification, and remaining
limitations. Use a short, meaningful commit message for each milestone and push it
to the configured GitHub remote. Report a push as successful only after Git confirms
it; keep planned features separate from implemented features.

## Step 1 — Select a focus and request an interview question

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
