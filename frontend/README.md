# Interview practice — first milestone

Start the existing Spring Boot application with `./mvnw spring-boot:run`, then open
`http://localhost:8080/`. Configure your existing AI provider credentials first.

Choose Java, a topic (Core Java or Spring Boot), and a level (beginner,
intermediate, advanced). Click **Get interview question** to request one question.
The defaults are Java → Spring Boot → intermediate.

The browser loads allowed selections from `GET /interview/options` and submits:

```sh
curl http://localhost:8080/interview/questions \
  -H 'Content-Type: application/json' \
  -d '{"track":"Java","topic":"Spring Boot","level":"intermediate"}'
```

Example response shape (question text varies):

```json
{
  "track": "Java",
  "topic": "Spring Boot",
  "level": "intermediate",
  "question": "How would you diagnose an auto-configuration that did not activate?"
}
```

`InterviewService.generateQuestion` validates selections and renders the classpath
`Prompt/InterviewQuestion` template. The system prompt asks for exactly one question
without an answer or hints. This is a model instruction, not a guarantee about
provider output. Empty results and provider errors return 502; invalid inputs
return 400. Question and chat calls use the model, temperature, and 1000-token limit set in
`ChatOptionsConfig`.

The UI renders output as text, disables duplicate submissions, and offers
cancellation, timeout handling, and retries. Browser cancellation does not cancel
an already-running provider call. No frontend build or browser API key is needed.

This milestone is stateless. Answers, rubric-based feedback, adaptive questions,
persisted sessions, dashboard progress, résumé extraction, document citations,
conversation memory, and controlled study-task tools are future steps.

Run `./mvnw test` for automated backend checks. Question tests use a local stub
model and do not contact the AI provider. The old controller test was updated to
match the existing `/chat` contract, and the context test no longer calls a live
provider. Live question quality still needs verification with your configured model.
