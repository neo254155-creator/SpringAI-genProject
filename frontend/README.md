# Intervie frontend

A dark interview-learning workspace using native JavaScript modules, custom
elements, CSS, SVG charts, and a canvas particle globe. Spring Boot serves all
assets. No React, frontend build, CDN assets, or runtime npm dependencies were added.

## Screens

| URL | Behavior |
| --- | --- |
| `/` | Landing page with clearly labeled demonstration data and conceptual readiness/time metrics. |
| `/dashboard.html` | Actual saved totals, recent-answer chart, topic averages, recent sessions, and score-based suggestions. |
| `/practice.html` | Existing saved interview workflow with new styling, stage indicators, local visit timer, and end confirmation. |
| `/history.html` | Paginated sessions/questions/answer attempts and complete saved feedback. |
| `/analytics.html` | Actual topic averages, recent score changes, bounded weekly session starts, and recent completion history. |
| `/recommendations.html` | Topic priorities calculated from actual saved averages; AI recommendation tools remain planned. |
| `/questions.html` | Clearly marked question-bank placeholder linking to real saved questions in history. |
| `/settings.html` | Browser-local motion preference; account/profile controls marked planned. |
| `/privacy.html` | Explains shared storage, provider data flow, local preference, and missing account/deletion controls. |

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
- `ui/api-config.js`: loads the public API base URL and supplies it centrally to requestJson.
- `coach.js` / `interview-client.js`: preserved selection → session → question → answer → feedback → next → complete/resume behavior.
- `history.js` / `history-client.js`: preserved history pagination, retries, and feedback inspection.
- `FrontendConfigController.java`: GET `/frontend-config` returns only `apiBaseUrl`.

## Backend URL

Same-origin requests are the default. To use a different public API origin, set
`FRONTEND_API_BASE_URL` in the environment launching Spring Boot, for example
`https://api.example.com`, then restart. The browser loads this public setting from
`/frontend-config`. Do not place keys/passwords in this variable. Credential-bearing
URLs, non-HTTP(S) protocols, query strings, and fragments are rejected by the client.
A separate origin requires suitable CORS/proxy configuration on that API; no broad
CORS policy or authentication mechanism was silently added.

## Data boundaries

- Total sessions and aggregate progress come from the real APIs.
- Performance charts show the latest saved answer from each of the 10 most recent
  sessions. This bounded sample is disclosed, including in the score-change card.
- Weekly activity counts session starts within those recent sessions, not all-time
  activity or active study duration. Completed-session elapsed time includes breaks.
- Topic averages are weighted by attempts and are not mastery/readiness scores.
- Only Core Java and Spring Boot are currently assessed as topics. Other requested
  skill areas are labeled not assessed; behavioral/HR/role interviews remain planned.
- Practice shows the real correctness/completeness/clarity rubric. Separate relevance,
  structure scores, improved example answers, and follow-up lists remain planned.
- Recommendations use a transparent lowest-average rule; no AI recommendation API exists.
- No login/profile API or ownership enforcement exists. Routes are a shared workspace,
  not falsely protected account pages. Authentication remains a separate backend task.
- Embeddings and document screens are outside this frontend milestone.
- The timer is local to the page visit; answer counts and workflow stages do not imply
  a fixed interview length. Unsubmitted drafts are not persisted.
- Stop waiting aborts the browser request; the server can still finish/save. Retrying
  an uncertain request can create another attempt because idempotency is not implemented.

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
./mvnw -o test
```
