/** Fixture-only browser verification. No Groq, Gemini, credentials, or real database. */
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
  console.error("Install optional Playwright tooling to run this fixture-only check. No browser checks ran.");
  process.exit(1);
}
const root = process.env.STATIC_ROOT ? path.resolve(process.env.STATIC_ROOT)
  : fileURLToPath(new URL("../src/main/resources/static/", import.meta.url));
const artifacts = fileURLToPath(new URL("./artifacts/", import.meta.url));
const doc = { id: "d1", title: "Java revision notes", filename: "java.md", createdAt: "2026-11-01T12:00:00Z" };
const other = { ...doc, id: "d2", title: "Spring Boot notes", filename: "spring.md" };
const uploaded = { ...doc, id: "d3", title: "Uploaded verification notes", filename: "verification.txt" };
const counts = { index: 0, answer: 0, upload: 0 };
let mode = "normal", hasUpload = false, browser, activePage, resumeUploads = 0;
const mime = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" };
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  const json = (value, status = 200) => { res.writeHead(status, { "Content-Type": "application/json" }).end(JSON.stringify(value)); };
  try {
    if (url.pathname === "/frontend-config") return json({ apiBaseUrl: "" });
    if (url.pathname === "/resumes" && req.method === "POST") {
      for await (const chunk of req) { /* Consume synthetic resume input only. */ }
      resumeUploads++;
      return json({ id: "r1", filename: "resume.txt", skills: [{ name: "Java", evidence: "Used Java." }] }, 201);
    }
    if (url.pathname === "/documents" && req.method === "GET") {
      if (mode === "list-error") return json({ detail: "Storage temporarily unavailable." }, 503);
      const page = Number(url.searchParams.get("page"));
      return json({ content: page === 0 ? (hasUpload ? [uploaded, doc] : [doc]) : [other], page, size: 10, totalPages: 2, totalElements: 11 });
    }
    if (url.pathname === "/documents" && req.method === "POST") {
      for await (const chunk of req) { /* Consume synthetic multipart input only. */ }
      counts.upload++; hasUpload = true;
      if (mode === "upload-list-error") mode = "list-error";
      return json(uploaded, 201);
    }
    const route = url.pathname.match(/^\/documents\/(d[123])(?:\/(index|answer))?$/);
    if (route) {
      const id = route[1], action = route[2];
      if (!action) return json(id === "d1" ? doc : id === "d2" ? other : uploaded);
      if (action === "index") {
        counts.index++;
        return json({ documentId: id, model: "fixture-embedding-model", dimensions: 3, passageCount: 1 });
      }
      counts.answer++;
      let body = ""; for await (const chunk of req) body += chunk;
      const { query } = JSON.parse(body);
      if (mode === "unindexed") return json({ detail: "Index this document before asking." }, 409);
      if (mode === "quota") return json({ detail: "Free-tier quota reached. Please try later." }, 429);
      if (mode === "slow") await new Promise((resolve) => setTimeout(resolve, 1000));
      const insufficient = mode === "insufficient";
      return json({ documentId: id, title: doc.title, query,
        answer: insufficient ? "The retrieved notes do not contain enough information to answer this question."
          : mode === "unsafe" ? '<img src=x onerror="window.notesInjected=true">' : "An interface defines a contract.",
        insufficientEvidence: insufficient,
        sources: insufficient ? [] : [{ passageId: "p1", number: 1,
          text: mode === "unsafe" ? "<script>window.notesInjected=true</script>" : "A Java interface defines a contract. It describes behaviour that implementing classes provide.", similarity: .81 }] });
    }
    const filename = path.resolve(root, "." + decodeURIComponent(url.pathname));
    if (path.relative(root, filename).startsWith("..")) { res.writeHead(403).end(); return; }
    const data = await readFile(filename);
    res.writeHead(200, { "Content-Type": mime[path.extname(filename)] || "application/octet-stream" }).end(data);
  } catch { if (!res.headersSent) res.writeHead(404).end(); }
});

try {
  await new Promise((resolve, reject) => { server.once("error", reject); server.listen(0, "127.0.0.1", resolve); });
  await mkdir(artifacts, { recursive: true });
  browser = await chromium.launch({ headless: true,
    ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const origin = `http://127.0.0.1:${server.address().port}`;
  // Prevent any unanticipated external request, including provider requests.
  await context.route("**/*", (route) => route.request().url().startsWith(origin + "/") ? route.continue() : route.abort());
  const page = await context.newPage(), errors = []; activePage = page;
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("requestfailed", (request) => {
    if (request.failure()?.errorText !== "net::ERR_ABORTED")
      console.error("Fixture request failed:", request.url(), request.failure()?.errorText);
  });
  const status = page.locator("#notes-status"), error = page.locator("#notes-error");
  const ask = page.getByRole("button", { name: "Ask my notes", exact: true });
  const question = page.getByLabel("What would you like to understand?");
  const waitText = (locator, text) => locator.filter({ hasText: text }).waitFor({ state: "visible" });
  await page.goto(origin + "/documents.html");
  await waitText(status, "Choose notes");
  assert.deepEqual(counts, { index: 0, answer: 0, upload: 0 });
  assert.equal(await page.getByRole("link", { name: "Résumé Interview", exact: true }).count(), 1);
  await page.getByRole("button", { name: /Java revision notes/ }).click();
  await question.fill("What is an interface?");
  mode = "unindexed"; await ask.click(); await waitText(error, "Index this document");
  assert.equal(counts.index, 0, "answer failures must not trigger automatic indexing");
  mode = "normal"; await page.getByRole("button", { name: "Index notes", exact: true }).click();
  await waitText(status, "Notes indexed");
  assert.equal(await page.getByRole("button", { name: "Index notes", exact: true }).isDisabled(), true);
  await ask.click(); await waitText(status, "Answer ready");
  assert.equal(await page.locator("#notes-answer-text").innerText(), "An interface defines a contract.");
  assert.equal(await page.locator("#notes-answer-query").innerText(), "Question: What is an interface?");
  assert.match(await page.locator("#notes-sources").innerText(), /A Java interface defines a contract/);
  const answered = counts.answer; await ask.click(); await waitText(status, "No new AI call");
  assert.equal(counts.answer, answered); assert.equal(counts.index, 1);
  await page.screenshot({ path: path.join(artifacts, "documents-desktop.png"), fullPage: true, animations: "disabled" });
  await page.setViewportSize({ width: 390, height: 900 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  await page.screenshot({ path: path.join(artifacts, "documents-mobile.png"), fullPage: true, animations: "disabled" });
  await page.setViewportSize({ width: 320, height: 800 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  await page.setViewportSize({ width: 1440, height: 1000 });
  mode = "unsafe"; await question.fill("Show unsafe text literally"); await ask.click(); await waitText(status, "Answer ready");
  assert.equal(await page.locator("#notes-answer img, #notes-answer script").count(), 0);
  assert.equal(await page.evaluate(() => Boolean(window.notesInjected)), false);
  mode = "insufficient"; await question.fill("An unsupported question?"); await ask.click();
  await waitText(page.locator("#notes-answer-title"), "More evidence needed");
  assert.equal(await page.locator("#notes-sources details").count(), 0);
  mode = "quota"; const beforeQuota = counts.answer;
  await question.fill("Another question?"); await ask.click(); await waitText(error, "Free-tier quota reached");
  assert.equal(counts.answer, beforeQuota + 1);
  assert.equal(await question.inputValue(), "Another question?");
  mode = "upload-list-error"; const beforeUpload = { ...counts };
  await page.getByLabel("Document title").fill(uploaded.title);
  await page.getByLabel("Text or Markdown file").setInputFiles({ name: "verification.txt", mimeType: "text/plain", buffer: Buffer.from("Synthetic notes.") });
  await page.getByRole("button", { name: "Save notes", exact: true }).click();
  await waitText(status, "Notes saved");
  await waitText(page.locator("#selected-title"), uploaded.title);
  await waitText(error, "Your notes were saved, but the list could not refresh");
  assert.equal(counts.upload, 1); assert.equal(counts.index, beforeUpload.index); assert.equal(counts.answer, beforeUpload.answer);
  mode = "normal"; await page.getByRole("button", { name: "Refresh saved notes", exact: true }).click();
  await waitText(status, "Saved notes refreshed");
  await page.getByRole("button", { name: "Next", exact: true }).click(); await waitText(page.locator("#notes-page"), "2 / 2");
  await page.getByRole("button", { name: /Spring Boot notes/ }).click();
  assert.equal(await question.inputValue(), ""); assert.equal(await page.locator("#notes-answer").isVisible(), false);
  const beforeReload = { ...counts }; await page.reload(); await waitText(status, "Choose notes");
  await waitText(page.locator("#selected-title"), "Spring Boot notes"); assert.deepEqual(counts, beforeReload);
  mode = "slow"; await question.fill("A slow question?"); await ask.click();
  assert.equal(await ask.isDisabled(), true);
  await page.getByRole("button", { name: "Stop waiting", exact: true }).click();
  await waitText(error, "server may still finish");
  mode = "list-error"; await page.getByRole("button", { name: "Refresh saved notes", exact: true }).click();
  await waitText(error, "Storage temporarily unavailable");
  mode = "normal"; await page.getByRole("button", { name: "Refresh saved notes", exact: true }).click();
  await waitText(status, "Saved notes refreshed");
  await page.goto(origin + "/practice.html");
  await page.locator("#resume-file").setInputFiles({ name: "resume.txt", mimeType: "text/plain", buffer: Buffer.from("Used Java.") });
  await page.getByRole("button", { name: "Extract my technical skills →", exact: true }).click();
  await page.getByRole("heading", { name: "Which skills should we explore?", exact: true }).waitFor({ state: "visible" });
  assert.equal(resumeUploads, 1, "the main resume upload flow must still work");
  assert.deepEqual(errors, []);
  console.log("PASS: mocked document workflow, cache/quota controls, text safety, pagination, recovery/cancellation, 1440/390/320px layouts, and main resume upload. No live provider calls.");
} catch (error) {
  console.error("Fixture browser state:", JSON.stringify({ counts,
    status: await activePage?.locator("#notes-status").textContent().catch(() => null),
    error: await activePage?.locator("#notes-error").textContent().catch(() => null) }));
  await activePage?.screenshot({ path: path.join(artifacts, "documents-failure.png"), fullPage: true }).catch(() => {});
  throw error;
} finally {
  await browser?.close();
  await new Promise((resolve) => server.close(resolve));
}
