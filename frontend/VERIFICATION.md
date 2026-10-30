# Frontend verification

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
