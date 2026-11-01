# Frontend verification

## Live résumé smoke — 2026-11-01

- The actual local project passed browser upload, grounded skill extraction, confirmation of Python, beginner selection, one question, scored feedback, page reload, completion, saved history, and progress through Groq and Neon. Exactly three outbound Groq requests returned HTTP 200; no Gemini call ran.
- The synthetic answer received 10/10 and reopened with the same answer and score. A completed `codex-live-check-20261101.txt` verification session remains in shared history.
- A temporary local forwarding proxy capped outbound requests at three. No automatic retry or second question was used; reopening saved data made no additional AI request. The test application was stopped afterward, and credentials were kept out of reports and commits.
- An initial launcher attempt omitted Java parameter metadata and failed before AI. Recompiling with Maven's `-parameters` setting fixed the launcher; production code was unchanged. Weakness practice remains verified by offline checks below; a single live example does not establish scoring consistency or database restart recovery.

## Full résumé journey — 2026-11-01

- `resume-browser-check.mjs` passed upload → confirmed skills → difficulty → answers → scored feedback → weakness practice → saved history/progress, with refresh and 390/320px checks.
- Stateful HTTP fixtures check explicit retry after first-question failure, retained input on a 429 error, and no AI mutations during saved-data reads. All external browser requests are blocked.
- Separately, all 19 `InterviewPersistenceTest` tests passed through Spring Boot/MockMvc/JPA/H2, including a new multipart-upload-to-progress API test; migrations V1–V5 applied successfully. The 38 client tests also passed.
- No live AI or Neon calls. These are complementary browser-fixture and API/database checks, not one browser-to-Neon run; provider quality, PostgreSQL-specific behavior, and database restart recovery remain unverified.
- Use `node frontend/resume-browser-check.mjs`. Like the document check, it accepts `PLAYWRIGHT_MODULE`, `BROWSER_EXECUTABLE`, and `STATIC_ROOT`; screenshots are ignored under frontend/artifacts/.
- The Windows backend run needed Mockito's JAR supplied with Surefire `-DargLine=-javaagent:<mockito-core.jar>` because dynamic agent attachment stalled. No production configuration was changed.

## Study Notes — 2026-11-01

- 38 frontend client tests passed, including 12 new document/cache/quota tests.
- The fixture-only documents-browser-check.mjs passed upload, indexing, cited
  answers, insufficient evidence, quota/errors, saved-upload recovery, cancellation,
  pagination, text safety, and 1440/390/320px layouts; main resume upload also passed.
- Desktop/mobile screenshots were inspected. Tests used a separate headless Brave
  instance and mocked APIs; no Groq/Gemini calls or real database writes occurred.
- Run with optional Playwright installed: `node frontend/documents-browser-check.mjs`.
  `BROWSER_EXECUTABLE` can select an installed Chromium browser. Screenshots are
  saved under ignored frontend/artifacts/. Live workflow quality remains unverified.

The older entries below describe verification at earlier milestones.

## Checks completed in the agent environment

- Maven packaging succeeded with the existing Java build.
- 126 backend tests passed using stubs/H2, including the public frontend-config route.
- 21 Node tests passed: 10 interview workflow, 3 history, 8 dashboard/config/chart tests.
- JavaScript syntax, local asset/import references, and whitespace were checked.

These checks do not establish browser layout, animation smoothness, or live Groq,
PostgreSQL, and embedding behavior.

## Browser checks: supplied, not executed here

This environment has no usable Playwright browser and denies local server sockets.
The Superdesign CLI download also failed due DNS/network restrictions. The public
Active Theory page returned only a JavaScript-required shell to the web reader.
The design follows the requested visual direction; exact reference matching and
visual motion quality have not been verified.

From your own terminal, install optional test tooling and run:

```sh
cd frontend
npm install --no-save --package-lock=false playwright
npx playwright install chromium
node browser-check.mjs
```

The script starts a temporary local static server and uses explicit test fixtures
for API responses. It never calls Groq or a real database. It checks:

- Landing at 1440, 768, 390, and 320 pixels, plus horizontal overflow.
- Workspace routes, chart keyboard tooltips, sidebar collapse/mobile navigation.
- Empty data, API failures, and retries.
- Topic preselection, question → answer → feedback → next-question flow.
- End-interview confirmation/cancellation and legacy session-URL resume.
- History navigation, reduced-motion behavior, and browser JavaScript errors.
- Screenshots saved under ignored `frontend/artifacts/` for visual review.

The script itself was syntax-checked. It exited with a missing-Playwright message
here; that is not a browser test pass. Screenshots have not been generated here.

## Real application review still required

Run Spring Boot with your database and chat configuration, then open:

- `/` — landing and explicitly labeled sample dashboard.
- `/dashboard.html` — real saved totals and recent scores.
- `/practice.html` — real interview flow and end confirmation.
- `/history.html` — existing saved question/answer/feedback browsing.
- `/analytics.html` — actual topic averages and scoped recent activity.
- `/recommendations.html` — score-based suggestions, not an AI recommendations endpoint.
- `/settings.html`, `/questions.html`, `/privacy.html` — preferences, planned question bank, and current privacy details.

Use keyboard navigation, a small phone viewport, and reduced-motion mode. Inspect
hero motion, reveal transitions, hover tilt, chart animation, modal focus/escape,
and mobile navigation. Confirm actual data survives refresh/resume. Test provider
and database failures without sharing credentials. Animation smoothness, touch
behavior, and screen-reader usability need real-browser review even if automation passes.
