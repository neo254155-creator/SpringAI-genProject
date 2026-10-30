/** Browser verification with explicitly mocked APIs. Never calls Groq or a real database. */
import http from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import assert from "node:assert/strict";
let chromium;
try {
  ({ chromium } = await import("playwright"));
} catch {
  console.error(
    "Browser check requires Playwright. See frontend/VERIFICATION.md. No browser checks ran.",
  );
  process.exit(1);
}
const root = fileURLToPath(
  new URL("../src/main/resources/static/", import.meta.url),
);
const artifacts = fileURLToPath(new URL("./artifacts/", import.meta.url));
const mime = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
};
const server = http.createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(
      new URL(req.url, "http://localhost").pathname,
    );
    const filename = path.resolve(
      root,
      "." + (pathname === "/" ? "/index.html" : pathname),
    );
    if (!filename.startsWith(root)) {
      res.writeHead(403).end();
      return;
    }
    const data = await readFile(filename);
    res.writeHead(200, {
      "Content-Type":
        mime[path.extname(filename)] || "application/octet-stream",
    });
    res.end(data);
  } catch {
    res.writeHead(404).end("Not found");
  }
});
let browser;
try {
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  await mkdir(artifacts, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const base = `http://127.0.0.1:${server.address().port}`;
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const errors = [];
  context.on("page", (p) => p.on("pageerror", (e) => errors.push(e.message)));
  const feedback = {
    correctPoints: ["Recognizes dependency injection."],
    missingPoints: ["Explain constructor injection."],
    explanation: "Constructor injection makes dependencies explicit.",
    rubric: {
      correctness: { score: 4, reason: "Mostly accurate." },
      completeness: { score: 2, reason: "One example is missing." },
      clarity: { score: 1, reason: "Explain the sequence more clearly." },
    },
    score: 7,
    maxScore: 10,
  };
  let mode = "filled",
    newQuestion = 0,
    completeCalls = 0;
  const profile = {
    id: "r1",
    filename: "resume.txt",
    skills: [
      { name: "Python", evidence: "Built tools with Python." },
      { name: "PostgreSQL", evidence: "Used PostgreSQL for storage." },
    ],
  };
  const session = {
    id: "s1",
    track: "Resume",
    topic: "Resume interview",
    resume: { resumeId: "r1", filename: "resume.txt", skills: profile.skills },
    level: "intermediate",
    status: "ACTIVE",
    createdAt: "2026-10-25T12:00:00Z",
    completedAt: null,
  };
  let question = {
    id: "q1",
    sessionId: "s1",
    skill: "Python",
    question: "How does a Python generator work?",
    createdAt: "2026-10-25T12:01:00Z",
    focus: null,
  };
  let answer = {
    id: "a1",
    questionId: "q1",
    answer: "Spring supplies dependencies.",
    submittedAt: "2026-10-25T12:02:00Z",
    rubricVersion: "1",
    feedback,
  };
  const pageOf = (items) => ({
    content: items,
    page: 0,
    size: 10,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
  });
  await context.route(
    /\/(interview\/|resumes(?:\/|$)|frontend-config)/,
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        p = url.pathname,
        method = req.method();
      if (mode === "error" && p !== "/frontend-config")
        return route.fulfill({
          status: 503,
          json: { detail: "Fixture server unavailable." },
        });
      let data;
      if (p === "/frontend-config") data = { apiBaseUrl: "" };
      else if (p === "/resumes/text" || p === "/resumes/r1") data = profile;
      else if (p === "/resumes/r1/sessions") {
        const selection = req.postDataJSON();
        session.resume.skills = profile.skills.filter((skill) =>
          selection.skills.includes(skill.name),
        );
        session.level = selection.level;
        session.status = "ACTIVE";
        session.completedAt = null;
        answer = null;
        data = session;
      } else if (p === "/interview/options")
        data = {
          tracks: ["Java"],
          topics: ["Core Java", "Spring Boot"],
          levels: ["beginner", "intermediate", "advanced"],
        };
      else if (p === "/interview/progress")
        data =
          mode === "empty"
            ? []
            : [
                {
                  track: "Java",
                  topic: "Python",
                  level: "intermediate",
                  attempts: 2,
                  questionsAnswered: 2,
                  averageScore: 7,
                  bestScore: 8,
                  lastPracticedAt: answer?.submittedAt,
                },
              ];
      else if (p === "/interview/sessions" && method === "GET")
        data = pageOf(mode === "empty" ? [] : [session]);
      else if (p === "/interview/sessions" && method === "POST") {
        Object.assign(session, req.postDataJSON(), {
          status: "ACTIVE",
          completedAt: null,
        });
        answer = null;
        data = session;
      } else if (p === "/interview/sessions/s1/resume")
        data = { session, question, answer, questionsAnswered: answer ? 1 : 0 };
      else if (p === "/interview/sessions/s1/questions" && method === "POST") {
        question = {
          ...question,
          id: `q${++newQuestion}`,
          question:
            newQuestion === 1
              ? "How does a Python generator work?"
              : "Explain Python generators clearly with an example.",
          focus:
            newQuestion > 1
              ? {
                  criterion: "clarity",
                  reason: "Practise clarity (1/2) from your latest feedback.",
                }
              : null,
        };
        answer = null;
        data = question;
      } else if (p === "/interview/sessions/s1/questions")
        data = pageOf([question]);
      else if (
        /^\/interview\/questions\/[^/]+\/answers$/.test(p) &&
        method === "POST"
      ) {
        answer = {
          id: "a2",
          questionId: question.id,
          answer: req.postDataJSON().answer,
          submittedAt: "2026-10-26T12:00:00Z",
          rubricVersion: "1",
          feedback,
        };
        data = answer;
      } else if (/^\/interview\/questions\/[^/]+\/answers$/.test(p))
        data = pageOf(answer ? [answer] : []);
      else if (p === "/interview/sessions/s1/complete") {
        completeCalls++;
        session.status = "COMPLETED";
        session.completedAt = "2026-10-26T12:30:00Z";
        data = session;
      } else
        return route.fulfill({
          status: 404,
          json: { detail: `Unexpected fixture route: ${method} ${p}` },
        });
      await route.fulfill({ json: data });
    },
  );
  const page = await context.newPage();
  async function noOverflow(label) {
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
      `Horizontal overflow: ${label}`,
    );
  }
  async function visible(locator) {
    await locator.waitFor({ state: "visible" });
  }
  async function ready() {
    await page.waitForFunction(
      () => !document.querySelector("#site .skeleton"),
    );
  }
  for (const width of [1440, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto(base);
    await visible(page.getByRole("heading", { name: /Your next interview/ }));
    await page
      .getByRole("heading", { name: /Turn every practice/ })
      .scrollIntoViewIfNeeded();
    await page.waitForTimeout(800);
    await noOverflow(`landing ${width}`);
    await page.screenshot({
      path: path.join(artifacts, `landing-${width}.png`),
      fullPage: true,
    });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  for (const route of [
    "dashboard",
    "analytics",
    "recommendations",
    "questions",
    "settings",
    "privacy",
    "history",
  ]) {
    await page.goto(`${base}/${route}.html`);
    await ready();
    if (["dashboard", "analytics", "recommendations"].includes(route))
      await page.waitForFunction(
        () =>
          !document
            .querySelector("#dashboard-data")
            ?.getAttribute("aria-busy") ||
          document
            .querySelector("#dashboard-data")
            .getAttribute("aria-busy") === "false",
      );
    await noOverflow(route);
    await page.screenshot({
      path: path.join(artifacts, `${route}.png`),
      fullPage: true,
    });
  }
  await page.goto(`${base}/dashboard.html`);
  await visible(page.getByText("Performance over time", { exact: true }));
  await page.locator(".chart-dot").first().focus();
  assert.match(await page.locator(".chart-tooltip").innerText(), /7\/10/);
  await page
    .getByRole("button", { name: "Collapse sidebar", exact: true })
    .click();
  assert.equal(
    await page
      .locator(".app-shell")
      .evaluate((n) => n.classList.contains("collapsed")),
    true,
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page
    .getByRole("button", { name: "Open navigation", exact: true })
    .click();
  await visible(page.locator('.sidebar a[href="/analytics.html"]'));
  await page.keyboard.press("Escape");
  assert.equal(
    await page.locator("#open-nav").getAttribute("aria-expanded"),
    "false",
  );
  await noOverflow("mobile dashboard");
  mode = "empty";
  await page.goto(`${base}/dashboard.html`);
  await visible(
    page.getByText("Your résumé interview starts here", { exact: true }),
  );
  assert.ok(
    !(await page.locator("#dashboard-data").innerText()).includes("8.6"),
  );
  mode = "error";
  await page.goto(`${base}/dashboard.html`);
  await visible(page.getByRole("button", { name: "Try again", exact: true }));
  mode = "filled";
  await page.getByRole("button", { name: "Try again", exact: true }).click();
  await visible(page.getByText("Performance over time", { exact: true }));
  await page.goto(`${base}/practice.html`);
  const setup = page.locator("resume-interview-setup");
  await visible(setup.locator("#extract"));
  await setup.locator("[name=source][value=text]").check();
  await setup
    .locator("#resume-text")
    .fill("Built tools with Python. Used PostgreSQL for storage.");
  await setup.locator("#extract").click();
  await visible(setup.locator("#review"));
  await setup.locator('#skills input[value="PostgreSQL"]').uncheck();
  await setup.locator("#level").selectOption("beginner");
  await setup.locator("#start-resume").click();
  const coach = page.locator("java-interview-coach");
  await visible(coach.locator("#question-section"));
  assert.deepEqual(
    session.resume.skills.map((skill) => skill.name),
    ["Python"],
  );
  assert.equal(await coach.locator(".fields").isVisible(), false);
  await coach
    .locator("#answer")
    .fill("A generator yields values lazily as they are requested.");
  await coach.locator("#submit-answer").click();
  await visible(coach.locator("#feedback-section"));
  assert.equal(await coach.locator("#score").innerText(), "7 / 10");
  await coach.locator("#next").click();
  await visible(coach.locator("#question-focus"));
  assert.match(await coach.locator("#question-focus").innerText(), /clarity/);
  await coach.locator("#finish").click();
  await visible(coach.locator("#end-dialog"));
  await coach.locator("#keep-practicing").click();
  assert.equal(completeCalls, 0);
  await coach.locator("#finish").click();
  await coach.locator("#confirm-end").click();
  await visible(coach.locator("#new"));
  assert.equal(completeCalls, 1);
  await noOverflow("mobile practice");
  await page.screenshot({
    path: path.join(artifacts, "practice-mobile.png"),
    fullPage: true,
  });
  await page.goto(`${base}/?session=s1`);
  await page.waitForURL("**/practice.html?session=s1");
  await visible(page.locator("java-interview-coach").locator("#new"));
  await page.goto(`${base}/history.html`);
  const history = page.locator("interview-history");
  await visible(
    history.getByRole("button", { name: "View questions & feedback" }),
  );
  await history
    .getByRole("button", { name: "View questions & feedback" })
    .click();
  await visible(history.getByRole("button", { name: "View answer attempts" }));
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(base);
  await visible(page.locator(".preview"));
  assert.equal(
    await page
      .locator(".float-card")
      .evaluate((n) => getComputedStyle(n).animationName),
    "none",
  );
  await page
    .getByRole("heading", { name: /Turn every practice/ })
    .scrollIntoViewIfNeeded();
  assert.equal(
    await page
      .getByRole("heading", { name: /Turn every practice/ })
      .evaluate((n) => getComputedStyle(n.closest(".reveal")).opacity),
    "1",
  );
  assert.deepEqual(errors, [], "Browser console errors");
  console.log(
    "PASS: mocked browser flows, responsive overflow checks, chart focus, navigation, error/empty states, confirmation, legacy resume, reduced motion. Screenshots saved to frontend/artifacts. This does not verify live providers or subjective motion quality.",
  );
} finally {
  await browser?.close();
  server.close();
}
