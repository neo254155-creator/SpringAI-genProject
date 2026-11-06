# Frontend verification

## Welcome-page release correction — 2026-11-06

- Live browser verification caught an empty `/` response from buffering the servlet welcome-page forward. Rolled back the affected deployment, then replaced buffering with headers applied on response commitment.
- Added forwarded-body and real packaged-homepage rendering regressions; 13 targeted security/cache tests, JAR packaging and the packaged production browser journey/restart passed. Existing client checks passed 57 tests. Repeat live browser/cache checks after promotion; no live AI generation is needed.

## Loading/cache preparation — 2026-11-06

- 12 targeted account/security/cache tests and 57 client tests passed; JAR packaging passed. Public files receive cache headers; APIs/private HTML/auth redirects/missing files, cookie responses, errors and Authorization/Range requests do not.
- A real browser against local HTTP fixtures tolerated 12-second configuration/account/CSRF responses and completed login; cancellation during shared CSRF setup blocks only that caller’s mutation. No live AI calls.
- Live baseline: homepage 14.04s; CSS 0.88s; configuration 0.72s, all no-store/MISS. Deployment requested by the user; verify deployed HIT/speed after release.

## Quota/input protection — 2026-11-06

- 185 default backend tests and 56 client tests passed, including shared quota/concurrency/failure/reset checks, guarded chat/embedding fixtures, source summaries, and 413 rejection without a retry key.
- Packaged production browser flow passed with six local fixture requests, including quota recovery and JDBC restart. An initial abrupt-restart check lost a recent H2 login; the test URL now explicitly uses WRITE_DELAY=0 (H2 defaults to 500ms). See https://h2database.com/html/commands.html#set_write_delay.
- Neon V9 migrated successfully. No live Groq/Gemini requests ran for this task; hosted generation still needs an explicit quota-conscious check.

## UI cleanup — 2026-11-05

- Removed decorative badges, sample metrics and internal setup/roadmap copy. Homepage/login passed at 1440/390/320px; 56 client tests and the résumé browser journey passed with fixtures. No live AI calls.

## Vercel production — 2026-11-05

- `https://intervie-genproject.vercel.app` is public and served from deployment `dpl_FWDFieEZbYmLkJuC4EVkiytT49sL`, reviewed commit `a91c69f`. Vercel built the Java 21 container successfully.
- Fixed observed startup timeouts with lazy initialization/repositories, C1 JVM compilation and one-time release migrations (V1–V8 were already verified on Neon). JPA schema validation stays enabled; see VERCEL.md before future schema releases.
- Browser checks passed: landing/assets, same-origin configuration, login redirect, registration/login, Secure/HttpOnly/SameSite cookies, CSRF denial, empty private history/progress, foreign-session 404, authenticated reload and logout. The public primary domain also passed in a fresh browser without Vercel cookies. Latest-deployment error/fatal logs were empty in the final checked window.
- Disposable test accounts/sessions and the final temporary share link were removed. Existing account/history preserved. No Groq/Gemini generation calls ran; hosted AI output is not verified by these checks. Transient test-client transport/assertion problems were corrected without changing product code.

## Essential flow — 2026-11-05

- 173 default offline backend tests and 56 client tests passed. The executable JAR packaged successfully; a running-JAR file lock required building the verification artifact in a separate folder.
- `core-flow-check.mjs` passed against the real packaged app and a disposable file-backed H2 database: registration/login, résumé upload, confirmed skills, difficulty, rubric feedback, weakness practice, history/dashboard, cross-account denial and process restart. The AI HTTP endpoint is a local fixture; six fixture requests, zero live provider calls per run.
- Both local servlet sessions and production JDBC sessions passed. Production login survived restart; local login expired and saved progress survived re-login. Secure cookies are disabled only in this disposable HTTP fixture; actual production configuration enables them.
- Neon V6–V8 migrated successfully. A production check verified Secure/HttpOnly/SameSite cookies, anonymous CSRF state across restart and logout cleanup; authenticated restart passed on H2. The user created the first real account: a read-only audit confirmed it owns the two existing sessions, with no unowned résumés/sessions/notes. Docker is unavailable; publishing waits until after UI work.
- The Java database-only connection helper passed against Neon and explicitly skipped AI. The Python launcher was reviewed but could not run because Python is not installed here.

## Accounts and ownership — 2026-11-02

- The full default offline backend suite passed: 173 tests, zero failures/errors/skips; live-provider and PostgreSQL-container tags were excluded. The suite covers session fixation/CSRF rotation, logout, foreign resource denial before AI, owner-scoped lists/progress and replay keys, password hashing, local-only first setup, and concurrent one-time legacy transfer.
- 56 client tests passed. Résumé and document browser fixtures passed with account/CSRF bootstrap; the résumé check also covers wrong-password errors, return links, sign-out, registration and a 320px account screen. Screenshots were inspected.
- The cookie/CSRF login and multipart retry also passed against the local embedded test server with H2/AI stubs. Browser flows use HTTP fixtures; no live Groq, Gemini or Neon request ran for this task.
- Migrations V1–V7 passed on H2. Actual Neon rollout/legacy transfer, HTTPS deployment and shared multi-instance session storage are pending. Creating the first real account is a user action after restarting the updated server; no real account/password was created by the agent.

## Retry protection — 2026-11-01

- 49 client tests passed, including key reuse after lost responses, multipart identity, reload recovery, schema validation, and clearing a recovered question key before the next question.
- 36 focused backend tests passed: 19 interview persistence, 10 replay/SDK, and 7 document-search tests. Concurrent duplicates, durable replay through a fresh filter, safe failure handling, and both embedding/chat replay counters passed. A real local HTTP multipart retry used different boundaries and extracted once.
- The configured SDK made exactly one attempt against a local HTTP 500 fixture. Both résumé and document browser checks passed and require UUID request keys on POSTs. AI models/providers and databases were fixtures/H2 only; no live Groq, Gemini, or Neon calls ran.
- V6 migrated successfully on H2. The earlier Neon smoke below predates V6; PostgreSQL rollout, crash/restart testing, and retention cleanup remain unverified.

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
