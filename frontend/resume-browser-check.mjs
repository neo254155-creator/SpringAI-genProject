/** Canonical resume journey with stateful HTTP fixtures. No real AI or database. */
import http from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";
import assert from "node:assert/strict";

let chromium;
try {
  ({ chromium } = await import(process.env.PLAYWRIGHT_MODULE
    ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : "playwright"));
} catch {
  console.error("Install optional Playwright tooling. No browser checks ran.");
  process.exit(1);
}
const root = process.env.STATIC_ROOT ? path.resolve(process.env.STATIC_ROOT)
  : fileURLToPath(new URL("../src/main/resources/static/", import.meta.url));
const artifacts = fileURLToPath(new URL("./artifacts/", import.meta.url));
const mime = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" };
const text = "Built tools with Python. Used PostgreSQL for storage.";
const profile = { id: "11111111-1111-1111-1111-111111111111", filename: "resume.txt", skills: [
  { name: "Python", evidence: "Built tools with Python." },
  { name: "PostgreSQL", evidence: "Used PostgreSQL for storage." },
] };
const firstAnswer = "A generator yields values lazily.";
const improvedAnswer = "For example, yield 1 pauses the generator until next() resumes it.";
const firstFeedback = { score: 7, maxScore: 10,
  correctPoints: ["Recognizes lazy evaluation."], missingPoints: ["Give a yield example."],
  explanation: "Use yield to produce one value at a time.", rubric: {
    correctness: { score: 4, reason: "Mostly accurate." },
    completeness: { score: 2, reason: "An example is missing." },
    clarity: { score: 1, reason: "Explain the sequence more clearly." },
  } };
const improvedFeedback = { ...firstFeedback, score: 10, missingPoints: [],
  rubric: { correctness: { score: 5, reason: "Accurate." },
    completeness: { score: 3, reason: "Includes an example." }, clarity: { score: 2, reason: "Clear sequence." } } };
const counts = { upload: 0, session: 0, question: 0, answer: 0, complete: 0 };
const unexpected = [], questions = [], answers = [];
let session = null, failFirstQuestion = true, quotaOnce = true, browser, page, stage = "startup";
const pageOf = (items, url) => {
  const page = Number(url.searchParams.get("page") || 0), size = Number(url.searchParams.get("size") || 20);
  return { content: items.slice(page * size, (page + 1) * size), page, size,
    totalElements: items.length, totalPages: Math.ceil(items.length / size) };
};
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost"), p = url.pathname, method = req.method;
  const json = (data, status = 200) => res.writeHead(status, { "Content-Type": "application/json" }).end(JSON.stringify(data));
  const body = async () => { let raw = ""; for await (const chunk of req) raw += chunk; return raw; };
  try {
    if (p === "/frontend-config" && method === "GET") return json({ apiBaseUrl: "" });
    if (p === "/resumes" && method === "POST") {
      counts.upload++;
      assert.match(req.headers["content-type"], /^multipart\/form-data; boundary=/);
      assert.ok((await body()).includes(text));
      return json(profile, 201);
    }
    if (p === `/resumes/${profile.id}` && method === "GET") return json(profile);
    if (p === `/resumes/${profile.id}/sessions` && method === "POST") {
      counts.session++;
      const selection = JSON.parse(await body());
      assert.deepEqual(selection, { skills: ["Python"], level: "beginner" });
      session = { id: "22222222-2222-2222-2222-222222222222", track: "Resume", topic: "Resume interview",
        level: selection.level, status: "ACTIVE", createdAt: "2026-11-01T12:00:00Z", completedAt: null,
        resume: { resumeId: profile.id, filename: profile.filename, skills: [profile.skills[0]] } };
      return json(session, 201);
    }
    if (p === "/interview/sessions" && method === "GET") return json(pageOf(session ? [session] : [], url));
    if (session && p === `/interview/sessions/${session.id}/resume` && method === "GET") {
      const question = questions.at(-1) || null;
      return json({ session, question, answer: answers.findLast((a) => a.questionId === question?.id) || null,
        questionsAnswered: new Set(answers.map((a) => a.questionId)).size });
    }
    if (session && p === `/interview/sessions/${session.id}/questions`) {
      if (method === "GET") return json(pageOf([...questions].reverse(), url));
      if (method === "POST") {
        counts.question++;
        if (failFirstQuestion) { failFirstQuestion = false; return json({ detail: "Fixture question provider unavailable." }, 503); }
        const followUp = questions.length > 0;
        const question = { id: `33333333-3333-3333-3333-${String(questions.length + 1).padStart(12, "0")}`,
          sessionId: session.id, skill: "Python", createdAt: `2026-11-01T12:0${questions.length + 1}:00Z`,
          question: followUp ? "Explain yield clearly using a small Python example." : "How does a Python generator work?",
          focus: followUp ? { criterion: "clarity", reason: "Practise clarity (1/2) from your latest feedback." } : null };
        questions.push(question); return json(question, 201);
      }
    }
    const questionId = p.match(/^\/interview\/questions\/([^/]+)\/answers$/)?.[1];
    if (questionId && questions.some((q) => q.id === questionId)) {
      if (method === "GET") return json(pageOf(answers.filter((a) => a.questionId === questionId), url));
      if (method === "POST") {
        counts.answer++;
        const submission = JSON.parse(await body());
        if (quotaOnce) { quotaOnce = false; return json({ detail: "Fixture free-tier quota reached. Try later." }, 429); }
        const answer = { id: `answer-${answers.length + 1}`, questionId, answer: submission.answer,
          submittedAt: `2026-11-01T12:0${answers.length + 2}:00Z`, rubricVersion: "1",
          feedback: answers.length ? improvedFeedback : firstFeedback };
        answers.push(answer); return json(answer, 201);
      }
    }
    if (session && p === `/interview/sessions/${session.id}/complete` && method === "POST") {
      counts.complete++; session.status = "COMPLETED"; session.completedAt = "2026-11-01T12:10:00Z"; return json(session);
    }
    if (p === "/interview/progress" && method === "GET") return json(answers.length ? [{ track: "Resume", topic: "Python",
      level: session.level, attempts: answers.length, questionsAnswered: new Set(answers.map((a) => a.questionId)).size,
      averageScore: answers.reduce((sum, a) => sum + a.feedback.score, 0) / answers.length,
      bestScore: Math.max(...answers.map((a) => a.feedback.score)), lastPracticedAt: answers.at(-1).submittedAt }] : []);
    if (/^\/(?:interview|resumes)(?:\/|$)/.test(p) || method !== "GET") {
      unexpected.push(`${method} ${p}`); return json({ detail: "Unexpected fixture API request." }, 404);
    }
    const filename = path.resolve(root, "." + decodeURIComponent(p === "/" ? "/index.html" : p));
    const relative = path.relative(root, filename);
    if (relative.startsWith("..") || path.isAbsolute(relative)) { res.writeHead(403).end(); return; }
    const data = await readFile(filename);
    res.writeHead(200, { "Content-Type": mime[path.extname(filename)] || "application/octet-stream" }).end(data);
  } catch (error) {
    unexpected.push(`${method} ${p}: ${error.message}`);
    if (!res.headersSent) json({ detail: "Fixture assertion or static-file error." }, 500);
  }
});

try {
  await new Promise((resolve, reject) => { server.once("error", reject); server.listen(0, "127.0.0.1", resolve); });
  await mkdir(artifacts, { recursive: true });
  browser = await chromium.launch({ headless: true,
    ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const origin = `http://127.0.0.1:${server.address().port}`, errors = [];
  await context.route("**/*", (route) => {
    if (route.request().url().startsWith(origin + "/")) return route.continue();
    unexpected.push("External request blocked"); return route.abort();
  });
  page = await context.newPage(); page.on("pageerror", (error) => errors.push(error.message));
  const setup = page.locator("resume-interview-setup"), coach = page.locator("java-interview-coach");
  const waitText = (locator, value) => locator.filter({ hasText: value }).waitFor({ state: "visible" });
  const noOverflow = async (label) => assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), label);

  stage = "upload and extracted skills";
  await page.goto(origin + "/practice.html");
  await setup.locator("#resume-file").setInputFiles({ name: "resume.txt", mimeType: "text/plain", buffer: Buffer.from(text) });
  await setup.locator("#extract").click();
  await setup.locator("#review").waitFor({ state: "visible" });
  assert.equal(await setup.locator("#skills input").count(), 2);
  assert.match(await setup.locator("#skills").innerText(), /Built tools with Python/);
  assert.equal(counts.session, 0, "extraction must wait for confirmation");
  await page.reload(); await setup.locator("#review").waitFor({ state: "visible" });
  assert.equal(counts.upload, 1, "opening saved skills must not extract again");

  stage = "confirm skills and choose difficulty";
  await setup.locator('#skills input[value="PostgreSQL"]').uncheck();
  await setup.locator("#start-resume").click();
  assert.equal(counts.session, 0, "difficulty must be chosen before creating a session");
  await setup.locator("#level").selectOption("beginner");
  await setup.locator('#skills input[value="Python"]').uncheck();
  await setup.locator("#start-resume").click();
  await waitText(setup.locator("#error"), "Select at least one extracted skill");
  assert.equal(counts.session, 0);
  await setup.locator('#skills input[value="Python"]').check();
  await setup.locator("#start-resume").click();
  await waitText(coach.locator("#start"), "Ask my first question");
  assert.equal(counts.question, 1, "a failed first question must not retry automatically");
  await coach.locator("#start").click(); await coach.locator("#question-section").waitFor({ state: "visible" });
  assert.equal(counts.session, 1, "retry must reuse the saved session");
  assert.deepEqual(session.resume.skills.map((s) => s.name), ["Python"]);
  assert.match(await coach.locator("#session-info").innerText(), /beginner/);
  assert.equal(await coach.locator("#next").isVisible(), false);

  stage = "answer, quota failure, and scored feedback";
  await coach.locator("#answer").fill(firstAnswer); await coach.locator("#submit-answer").click();
  await waitText(coach.locator("#error"), "free-tier quota reached");
  assert.equal(await coach.locator("#answer").inputValue(), firstAnswer);
  assert.equal(counts.answer, 1); assert.equal(answers.length, 0);
  await coach.locator("#submit-answer").click(); await waitText(coach.locator("#score"), "7 / 10");
  assert.match(await coach.locator("#missing-points").innerText(), /yield example/);
  const beforeRefresh = { ...counts };
  await page.reload(); await waitText(coach.locator("#score"), "7 / 10");
  assert.equal(await coach.locator("#answer").inputValue(), firstAnswer);
  assert.equal(await coach.locator("#answer").isDisabled(), true);
  assert.deepEqual(counts, beforeRefresh, "reload must only read saved data");

  stage = "practise the weakness";
  await coach.locator("#next").click(); await waitText(coach.locator("#question-focus"), "clarity");
  assert.equal(await coach.locator("#answer").inputValue(), "");
  await coach.locator("#answer").fill(improvedAnswer); await coach.locator("#submit-answer").click();
  await waitText(coach.locator("#score"), "10 / 10");
  await page.screenshot({ path: path.join(artifacts, "resume-feedback-desktop.png"), fullPage: true, animations: "disabled" });
  for (const width of [390, 320]) { await page.setViewportSize({ width, height: 900 }); await noOverflow(`practice at ${width}px`); }

  stage = "complete and reopen saved interview";
  await coach.locator("#finish").click(); await coach.locator("#keep-practicing").click(); assert.equal(counts.complete, 0);
  await coach.locator("#finish").click(); await coach.locator("#confirm-end").click();
  await coach.locator("#new").waitFor({ state: "visible" }); assert.equal(counts.complete, 1);
  const afterCompletion = { ...counts };
  await page.reload(); await coach.locator("#new").waitFor({ state: "visible" });
  assert.match(await coach.locator("#session-info").innerText(), /2 answered/);
  assert.equal(await coach.locator("#submit-answer").isVisible(), false);

  stage = "review saved history and progress";
  await page.goto(origin + "/history.html");
  const history = page.locator("interview-history");
  await waitText(history.locator("#progress-body .metric").filter({ hasText: "Average score" }), "8.5 / 10");
  await history.getByRole("button", { name: "View questions & feedback", exact: true }).click();
  await history.locator("#questions-body article").nth(1).waitFor({ state: "visible" });
  assert.equal(await history.locator("#questions-body article").count(), 2);
  for (const [index, score, answer] of [[0, 10, improvedAnswer], [1, 7, firstAnswer]]) {
    await history.locator("#questions-body article").nth(index).getByRole("button", { name: "View answer attempts" }).click();
    await waitText(history.locator("#answers-body"), `Score ${score} / 10`);
    assert.ok((await history.locator("#answers-body").innerText()).includes(answer));
    await history.getByText("Rubric breakdown", { exact: true }).click();
    await waitText(history.locator("#answers-body details"), `Clarity: ${score === 10 ? 2 : 1} / 2`);
  }
  await noOverflow("history at 320px");
  await page.screenshot({ path: path.join(artifacts, "resume-history-mobile.png"), fullPage: true, animations: "disabled" });
  await page.goto(origin + "/dashboard.html");
  await waitText(page.locator(".stat-card").filter({ hasText: "Average score" }), "8.5");
  await waitText(page.locator(".stat-card").filter({ hasText: "Questions answered" }), "2");
  await waitText(page.locator("#dashboard-data"), "10/10");
  assert.deepEqual(counts, afterCompletion, "saved history and dashboards must not call AI or mutate sessions");
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, []);
  console.log("PASS: upload → confirmed skills → difficulty → questions → scored feedback → weakness practice → saved history/progress; refresh, explicit retries, quota error and mobile checks. Stateful fixtures only; zero live AI calls.");
} catch (error) {
  console.error("Failed stage:", stage, JSON.stringify({ counts, unexpected }));
  await page?.screenshot({ path: path.join(artifacts, "resume-failure.png"), fullPage: true }).catch(() => {});
  throw error;
} finally {
  await browser?.close();
  await new Promise((resolve) => server.close(resolve));
}
