/** Real browser -> packaged Spring Boot -> file database -> restart. Local AI fixture only. */
import http from "node:http";
import { spawn } from "node:child_process";
import { mkdtemp, access, rm, writeFile, mkdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { once } from "node:events";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import assert from "node:assert/strict";

const root = fileURLToPath(new URL("../", import.meta.url));
const jar = path.resolve(process.env.APP_JAR || path.join(root, "target/genProject-0.0.1-SNAPSHOT.jar"));
if (!process.env.H2_JAR) throw new Error("Set H2_JAR to the test-only H2 driver. Build the application first.");
const h2 = path.resolve(process.env.H2_JAR);
await Promise.all([access(jar), access(h2)]);
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE
  ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : "playwright");
const work = await mkdtemp(path.join(tmpdir(), "intervie-core-"));
const password = "Disposable offline password 123!";
const answer = "A generator yields values lazily and resumes when next() is called.";
const counts = { extraction: 0, questions: 0, feedback: 0 };
const production = process.env.CORE_PROFILE === "production";
let app, browser, logs = "", stage = "startup", origin, page;
const errors = [];
const feedback = score => JSON.stringify({ correctPoints: ["Explains lazy iteration"],
  missingPoints: score === 7 ? ["Give a concrete yield example"] : [],
  explanation: "Use yield to pause and next() to resume.", rubric: {
    correctness: { score: score === 7 ? 4 : 5, reason: "Describes iteration" },
    completeness: { score: score === 7 ? 2 : 3, reason: "Covers the main idea" },
    clarity: { score: score === 7 ? 1 : 2, reason: "Include an example" },
  } });
const provider = http.createServer(async (req, res) => {
  try {
    assert.equal(req.method, "POST"); assert.match(req.url, /\/chat\/completions$/);
    let body = ""; for await (const chunk of req) body += chunk;
    const request = JSON.parse(body), system = request.messages.filter(m => m.role === "system").map(m => m.content).join("\n");
    let content;
    if (system.includes("Extract explicitly mentioned technical skills")) {
      counts.extraction++;
      content = JSON.stringify({ skills: [{ name: "Python", evidence: "Built tools with Python." },
        { name: "PostgreSQL", evidence: "Used PostgreSQL for storage." }] });
    } else if (system.includes("Use this rubric")) {
      counts.feedback++;
      if (counts.feedback === 1) {
        res.writeHead(429, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: { message: "Offline quota fixture", type: "rate_limit_error" } })); return;
      }
      content = feedback(counts.feedback === 2 ? 7 : 10);
    } else {
      counts.questions++;
      content = counts.questions === 1 ? "How does a Python generator produce values lazily?"
        : "Show how yield and next() cooperate using a small Python example.";
    }
    assert.ok(counts.extraction + counts.questions + counts.feedback <= 6, "Unexpected automatic AI attempt");
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ id: "offline-fixture", object: "chat.completion", created: 1,
      model: "offline-fixture", choices: [{ index: 0, message: { role: "assistant", content }, finish_reason: "stop" }],
      usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 } }));
  } catch (error) { errors.push(error.message); res.writeHead(500).end(); }
});
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function start() {
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (/^(DB_|GROQ_|DOCUMENT_EMBEDDINGS_|SPRING_)/.test(key)) delete env[key];
  logs = "";
  app = spawn(process.env.JAVA_EXECUTABLE || "java", [
    `-Dloader.path=${h2}`, "-cp", jar, "org.springframework.boot.loader.launch.PropertiesLauncher",
    "--server.address=127.0.0.1", "--server.port=0", "--spring.config.import=",
    `--spring.datasource.url=jdbc:h2:file:${path.join(work, "data").replaceAll("\\", "/")};MODE=PostgreSQL;WRITE_DELAY=0`,
    "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
    "--spring.ai.openai.api-key=offline-fixture", "--spring.ai.openai.chat.model=offline-fixture",
    `--spring.ai.openai.base-url=http://127.0.0.1:${provider.address().port}`,
    "--document-search.embedding.enabled=false",
    // Only the disposable HTTP test disables Secure; real production requires HTTPS.
    ...(production ? ["--spring.profiles.active=production", "--server.servlet.session.cookie.secure=false"] : []),
  ], { cwd: root, env, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
  let launchError; app.on("error", error => { launchError = error; });
  for (const output of [app.stdout, app.stderr]) output.on("data", data => { logs = (logs + data).slice(-300000); });
  for (let i = 0; i < 240; i++) {
    if (launchError) throw launchError;
    if (app.exitCode !== null) throw new Error("Application exited during startup");
    const port = logs.match(/Tomcat started on port (\d+)/)?.[1];
    if (port) { origin = `http://127.0.0.1:${port}`; return; }
    await pause(250);
  }
  throw new Error("Application startup timed out");
}
async function stop() {
  if (!app || app.exitCode !== null) return;
  const exited = once(app, "exit"); app.kill();
  await Promise.race([exited, pause(10000).then(() => { if (app.exitCode === null) throw new Error("Application did not stop"); })]);
}
async function login(username, register = false) {
  await page.goto(origin + "/login.html");
  if (register) await page.locator("#toggle-auth").click();
  await page.locator("#username").fill(username); await page.locator("#password").fill(password);
  await page.locator("#submit-auth").click(); await page.waitForURL("**/practice.html");
  await page.locator("resume-interview-setup").waitFor();
}
async function json(url) {
  const response = await page.request.get(origin + url); assert.equal(response.status(), 200); return response.json();
}
try {
  await new Promise(resolve => provider.listen(0, "127.0.0.1", resolve));
  await start();
  browser = await chromium.launch({ headless: true,
    ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.route("**/*", route => route.request().url().startsWith(origin + "/") ? route.continue() : route.abort());
  page = await context.newPage(); page.on("pageerror", error => errors.push(error.message));
  assert.equal((await page.request.get(origin + "/interview/sessions")).status(), 401);
  assert.equal((await page.request.post(origin + "/auth/register", { data: {} })).status(), 403);
  stage = "registration and real session cookie";
  await login("offline-owner", true);
  const cookie = (await context.cookies()).find(c => c.name === "INTERVIE_SESSION");
  assert.ok(cookie?.httpOnly); assert.equal(cookie.sameSite, "Lax");
  stage = "résumé upload and confirmation";
  const setup = page.locator("resume-interview-setup"), coach = page.locator("java-interview-coach");
  await setup.locator("#resume-file").setInputFiles({ name: "offline-resume.txt", mimeType: "text/plain",
    buffer: Buffer.from("Built tools with Python. Used PostgreSQL for storage.") });
  await setup.locator("#extract").click(); await setup.locator("#review").waitFor({ state: "visible" });
  assert.equal(await setup.locator("#skills input").count(), 2);
  await setup.locator('#skills input[value="PostgreSQL"]').uncheck();
  await setup.locator("#level").selectOption("beginner"); await setup.locator("#start-resume").click();
  await coach.locator("#question-section").waitFor({ state: "visible" });
  const session = new URL(page.url()).searchParams.get("session"); assert.ok(session);
  stage = "quota recovery without hidden retries";
  await coach.locator("#answer").fill(answer); await coach.locator("#submit-answer").click();
  await coach.locator("#error").waitFor({ state: "visible" }); assert.equal(counts.feedback, 1);
  assert.equal(await coach.locator("#answer").inputValue(), answer);
  await coach.locator("#submit-answer").click();
  await coach.locator("#score").filter({ hasText: "7 / 10" }).waitFor();
  stage = "weakness practice and persistence";
  await coach.locator("#next").click(); await coach.locator("#question-focus").filter({ hasText: "clarity" }).waitFor();
  await coach.locator("#answer").fill("For example, yield 1 pauses a generator and next() resumes it.");
  await coach.locator("#submit-answer").click(); await coach.locator("#score").filter({ hasText: "10 / 10" }).waitFor();
  const beforeReads = { ...counts };
  await page.reload(); await coach.locator("#score").filter({ hasText: "10 / 10" }).waitFor();
  const saved = await json(`/interview/sessions/${session}/resume`);
  assert.equal(saved.questionsAnswered, 2); assert.equal(saved.answer.feedback.score, 10);
  assert.deepEqual(saved.session.resume.skills.map(s => s.name), ["Python"]);
  await coach.locator("#finish").click(); await coach.locator("#confirm-end").click(); await coach.locator("#new").waitFor();
  await page.goto(origin + "/history.html");
  await page.locator("interview-history #progress-body").filter({ hasText: "8.5 / 10" }).waitFor();
  await page.goto(origin + "/dashboard.html"); await page.locator(".stat-card").filter({ hasText: "Average score" }).filter({ hasText: "8.5" }).waitFor();
  assert.deepEqual(counts, beforeReads, "Saved-data reads must not call AI");
  stage = "logout and cross-account isolation";
  await page.locator("#sign-out").click(); await page.waitForURL("**/login.html");
  await login("offline-second", true);
  assert.equal((await json("/interview/sessions")).totalElements, 0);
  assert.equal((await page.request.get(origin + `/interview/sessions/${session}/resume`)).status(), 404);
  assert.deepEqual(counts, beforeReads);
  stage = "process restart and saved progress";
  await stop(); await start();
  assert.equal((await page.request.get(origin + "/auth/me")).status(), production ? 200 : 401);
  if (production) assert.equal((await json("/auth/me")).username, "offline-second", "JDBC login must survive restart");
  await login("offline-owner");
  const restored = await json(`/interview/sessions/${session}/resume`);
  assert.equal(restored.session.status, "COMPLETED"); assert.equal(restored.questionsAnswered, 2);
  assert.equal(restored.answer.feedback.score, 10);
  assert.equal((await json("/interview/sessions")).totalElements, 1);
  assert.deepEqual(counts, { extraction: 1, questions: 2, feedback: 3 }); assert.deepEqual(errors, []);
  console.log(`PASS (${production ? "production JDBC sessions" : "local servlet sessions"}): packaged app + real browser + file database: accounts, résumé-to-progress flow, quota recovery, isolation and process restart. Six local fixture calls; zero live AI calls.`);
} catch (error) {
  await mkdir(path.join(root, "target"), { recursive: true });
  await writeFile(path.join(root, "target/core-flow-failure.log"), `Stage: ${stage}\n${logs}`);
  console.error(`FAIL at ${stage}: ${error.message}. See target/core-flow-failure.log.`); process.exitCode = 1;
} finally {
  try { await browser?.close(); } finally {
    await stop(); provider.closeAllConnections(); await new Promise(resolve => provider.close(resolve));
    if (path.dirname(work) !== path.resolve(tmpdir()) || !path.basename(work).startsWith("intervie-core-"))
      throw new Error("Unexpected temporary directory; cleanup skipped");
    await rm(work, { recursive: true, force: true });
  }
}
