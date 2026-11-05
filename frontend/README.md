# Intervie frontend

A dark interview-learning workspace using native JavaScript modules, custom
elements, CSS, SVG charts, and a canvas particle globe. Spring Boot serves all
assets. No React, frontend build, CDN assets, or runtime npm dependencies were added.

## Screens

| URL | Behavior |
| --- | --- |
| `/` | Landing page with clearly labeled demonstration data and conceptual readiness/time metrics. |
| `/login.html` | Username/password registration and sign-in, with safe return links. |
| `/dashboard.html` | Actual saved totals, recent-answer chart, topic averages, recent sessions, and score-based suggestions. |
| `/practice.html` | Existing saved interview workflow with new styling, stage indicators, local visit timer, and end confirmation. |
| `/history.html` | Paginated sessions/questions/answer attempts and complete saved feedback. |
| `/documents.html` | Optional study notes: upload, browse, explicitly index, ask questions, and inspect cited excerpts. |
| `/analytics.html` | Actual topic averages, recent score changes, bounded weekly session starts, and recent completion history. |
| `/recommendations.html` | Topic priorities calculated from actual saved averages; AI recommendation tools remain planned. |
| `/questions.html` | Clearly marked question-bank placeholder linking to real saved questions in history. |
| `/settings.html` | Browser-local motion preference and signed-in account status. |
| `/privacy.html` | Explains account ownership, stored retry results, provider data flow, and remaining deletion/reset limits. |

Old `/?session=ID` links redirect to `/practice.html?session=ID` and preserve the
existing read-only resume operation. Starting another interview clears the saved
session URL. A topic/level link can preselect supported values from the options API.

## Architecture and files

- `ui/tokens.css`: colors, typography, and shared design variables.
- `ui/design.css`: landing, application shell, charts, motion, and responsive layouts.
- `ui/workspace.css`: common styling for the existing coach/history shadow roots.
- `ui/components.js`: icons, navigation shell, safe text helpers, SVG chart/table, and empty states.
- `ui/motion.js`: reveal observers, pointer tilt, counters, motion preference, and a particle canvas that pauses offscreen/in hidden tabs.
- `ui/landing.js`: landing copy and explicitly isolated sample preview data.
- `ui/app.js`: page initialization, API configuration, settings, privacy, placeholders, and existing custom elements.
- `ui/dashboard-client.js`: bounded reads through real session/progress/resume endpoints.
- `ui/analytics.js`: pure weighted averages, time ordering, score changes, activity, and topic priorities.
- `ui/dashboard.js`: overview, analytics, recommendations, loading/error/empty/partial states.
- `ui/documents.js` / `ui/documents.css`: optional study-note workspace and responsive styling.
- `ui/documents-client.js`: validated document requests and bounded per-visit caching of successful indexing/answers.
- `ui/api-config.js`: loads the public API base URL and supplies it centrally to requestJson.
- `coach.js` / `interview-client.js`: preserved selection → session → question → answer → feedback → next → complete/resume behavior.
- `history.js` / `history-client.js`: preserved history pagination, retries, and feedback inspection.
- `FrontendConfigController.java`: GET `/frontend-config` returns only `apiBaseUrl`.

## Backend URL

Use the same origin for account pages and APIs; leave `FRONTEND_API_BASE_URL` empty
for the normal Spring Boot setup. A same-origin proxy can also be used. The public
setting comes from `/frontend-config` and must contain no keys/passwords. Account
requests reject other origins; credentialed cross-origin CORS is not configured.

## Data boundaries

- Total sessions and aggregate progress come from the real APIs.
- Performance charts show the latest saved answer from each of the 10 most recent
  sessions. This bounded sample is disclosed, including in the score-change card.
- Weekly activity counts session starts within those recent sessions, not all-time
  activity or active study duration. Completed-session elapsed time includes breaks.
- Topic averages are weighted by attempts and are not mastery/readiness scores.
- Résumé interviews use confirmed extracted skills. The legacy manual-selection
  API offers Core Java and Spring Boot; behavioral/HR/role interviews remain planned.
- Practice shows the real correctness/completeness/clarity rubric. Separate relevance,
  structure scores, improved example answers, and follow-up lists remain planned.
- Recommendations use a transparent lowest-average rule; no AI recommendation API exists.
- Sign-in, sign-out and registration use session cookies/CSRF. Stored resources and
  progress are scoped to the signed-in account; the first local account gets legacy data once.
- Study Notes is separate from the main resume-based interview journey. Listing/uploading makes no AI calls; indexing and new questions require explicit clicks. No automatic provider retries run. Per-visit cache clears on refresh.
- The timer is local to the page visit; answer counts and workflow stages do not imply
  a fixed interview length. Unsubmitted drafts are not persisted.
- Stop waiting aborts the browser request; the server can still finish/save. Retrying
  an uncertain request reuses its key; running/unknown outcomes remain blocked until reviewed.

## Motion and accessibility

Uses CSS entrance, gradient/float, hover/press, chart drawing and progress animations;
IntersectionObserver reveals/counters; and requestAnimationFrame canvas motion.
Respects system reduced motion and an optional browser-local preference. Fine-pointer
hover tilt is not attached on touch devices. Charts include keyboard point tooltips
and accessible data tables. Mobile navigation supports focus containment and Escape;
end confirmation uses a native dialog. Form labels, status/error announcements,
visible focus styles, and text-safe model output are retained.

## Verification

See [VERIFICATION.md](VERIFICATION.md) for completed checks, blocked browser checks,
and the runnable browser verification script. Offline commands:

```sh
node frontend/interview-client.test.mjs
node frontend/history-client.test.mjs
node frontend/dashboard.test.mjs
node frontend/documents-client.test.mjs
./mvnw -o test
```

`node frontend/documents-browser-check.mjs` runs the document UI with fixtures and
also checks the main resume upload. Requires optional Playwright tooling; see
VERIFICATION.md. It never calls Groq, Gemini, or a real database.

`node frontend/resume-browser-check.mjs` checks the complete résumé journey with
stateful HTTP fixtures, including saved history/progress, refresh, explicit retries,
quota errors, and mobile layouts. It uses the same optional Playwright setup and
environment overrides as the document check. For separate API/JPA/H2 verification,
run `./mvnw -Dtest=InterviewPersistenceTest test`; the AI model is stubbed.

`ui/retry-keys.js` and `retry-keys.test.mjs` cover pending request keys across
transport failures/reloads. `requestJson` handles JSON and multipart POSTs with
safe retries; see PERSISTENCE.md for the server contract. Run
`node --test frontend/retry-keys.test.mjs` without provider calls.

`ui/auth-client.js` handles session/CSRF requests; `auth.js`/`auth.css` power the
login screen. `auth-client.test.mjs` covers CSRF, safe redirects and cookie options.
Browser fixtures cover sign-in, registration and sign-out alongside résumé practice.

`core-flow-check.mjs` drives a real packaged Spring Boot app through the browser,
using a disposable file-backed H2 database and local AI HTTP fixture. It checks the
main journey, quota recovery, account isolation and process restart. Build first;
set `H2_JAR` to the cached test driver and run `node frontend/core-flow-check.mjs`.
Use `CORE_PROFILE=production` to check JDBC session survival; Secure cookies are
disabled only in this disposable HTTP fixture. Optional overrides: `APP_JAR`,
`JAVA_EXECUTABLE`, `PLAYWRIGHT_MODULE`, `BROWSER_EXECUTABLE`. No live providers run.
