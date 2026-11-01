import test from "node:test";
import assert from "node:assert/strict";
import { DocumentsClient, DocumentFlow, validateUpload } from "../src/main/resources/static/ui/documents-client.js";

const document = { id: "d1", title: "Java notes", filename: "java.md", createdAt: "2026-11-01T12:00:00Z" };
const index = { documentId: "d1", model: "test-model", dimensions: 3, passageCount: 1 };
const answer = (id = "d1", query = "What is an interface?") => ({ documentId: id, title: "Java notes", query,
  answer: "An interface defines a contract.", insufficientEvidence: false,
  sources: [{ passageId: "p1", number: 1, text: "An interface defines a contract.", similarity: .8 }] });
const ok = (data) => ({ ok: true, status: 200, json: async () => data });

test("upload validation rejects blank, unsupported, empty and oversized input before making requests", async () => {
  assert.equal(validateUpload(" Notes ", new File(["text"], "notes.md")), "Notes");
  for (const [title, file] of [[" ", new File(["x"], "a.txt")], ["a", new File(["x"], "a.pdf")],
    ["a", new File([], "a.txt")], ["a", new File(["x".repeat(65537)], "a.md")], ["a\0", null]]) {
    assert.throws(() => validateUpload(title, file));
  }
  let calls = 0;
  const client = new DocumentsClient(async () => { calls++; return ok(document); });
  await assert.rejects(client.upload("", null));
  assert.equal(calls, 0);
});

test("document browsing uses bounded read requests and no index/answer calls", async () => {
  const requests = [], controller = new AbortController();
  const client = new DocumentsClient(async (url, options) => {
    requests.push([url, options]);
    return ok(url.includes("?") ? { content: [document], page: 0, totalPages: 1, totalElements: 1 } : document);
  });
  assert.equal((await client.list(0, controller.signal)).content[0].id, "d1");
  await client.get("d1", controller.signal);
  assert.deepEqual(requests.map(([url]) => url), ["/documents?page=0&size=10", "/documents/d1"]);
  assert.ok(requests.every(([, options]) => options.method === "GET" && options.signal === controller.signal));
  await assert.rejects(client.list(-1));
});

test("upload sends multipart without replacing the generated boundary", async () => {
  const client = new DocumentsClient(async function (url, options) {
    assert.equal(this, undefined, "native fetch must not receive the client object as its receiver");
    assert.equal(url, "/documents"); assert.equal(options.method, "POST");
    assert.equal(options.body.get("title"), "Notes");
    assert.equal(options.body.get("file").name, "notes.txt");
    assert.equal(options.headers["Content-Type"], undefined);
    return ok(document);
  });
  assert.equal((await client.upload(" Notes ", new File(["notes"], "notes.txt"))).id, "d1");
});

test("index requests validate matching document, dimensions, and passage count", async () => {
  let result = index;
  const client = new DocumentsClient(async (url, options) => {
    assert.equal(url, "/documents/d1/index"); assert.equal(options.method, "POST"); return ok(result);
  });
  await client.index("d1");
  for (const invalid of [{ ...index, documentId: "other" }, { ...index, dimensions: 0 }, { ...index, passageCount: 0 }]) {
    result = invalid; await assert.rejects(client.index("d1"));
  }
});

test("answer uses the selected document, trimmed query, bounded retrieval and cancellation signal", async () => {
  const controller = new AbortController();
  const client = new DocumentsClient(async (url, options) => {
    assert.equal(url, "/documents/d1/answer"); assert.equal(options.method, "POST");
    assert.deepEqual(JSON.parse(options.body), { query: "What is an interface?", limit: 5 });
    assert.equal(options.signal, controller.signal); return ok(answer());
  });
  assert.equal((await client.answer("d1", " What is an interface? ", controller.signal)).sources[0].passageId, "p1");
});

test("malformed and contradictory citations are rejected instead of displayed or cached", async () => {
  let result;
  const client = new DocumentsClient(async () => ok(result));
  for (const invalid of [{ ...answer(), documentId: "d2" }, { ...answer(), query: "different" },
    { ...answer(), sources: [] }, { ...answer(), insufficientEvidence: true },
    { ...answer(), sources: [answer().sources[0], answer().sources[0]] },
    { ...answer(), sources: [{ ...answer().sources[0], similarity: 2 }] }]) {
    result = invalid; await assert.rejects(client.answer("d1", "What is an interface?"));
  }
  result = { ...answer(), insufficientEvidence: true, sources: [] };
  assert.equal((await client.answer("d1", "What is an interface?")).insufficientEvidence, true);
});

test("a document is indexed at most once per visit through the workflow", async () => {
  let calls = 0;
  const flow = new DocumentFlow({ index: async () => { calls++; return index; } });
  assert.equal(await flow.index("d1"), index);
  assert.equal(await flow.index("d1"), index);
  assert.equal(calls, 1);
});

test("successful repeated questions use cache, scoped to document and exact trimmed question", async () => {
  let calls = 0;
  const flow = new DocumentFlow({ answer: async (id, query) => { calls++; return answer(id, query.trim()); } });
  assert.equal((await flow.answer("d1", "Question?")).cached, false);
  assert.equal((await flow.answer("d1", " Question? ")).cached, true);
  assert.equal((await flow.answer("d2", "Question?")).cached, false);
  assert.equal(calls, 2);
});

test("concurrent provider actions are rejected before another request is made", async () => {
  let resolve, calls = 0;
  const flow = new DocumentFlow({ index: () => { calls++; return new Promise((r) => { resolve = r; }); } });
  const pending = flow.index("d1");
  await assert.rejects(flow.index("d1"), /wait/);
  assert.equal(calls, 1); resolve(index); await pending; assert.equal(flow.busy, false);
});

test("failures are not cached or automatically retried; quota errors remain visible", async () => {
  let calls = 0;
  const flow = new DocumentFlow(new DocumentsClient(async () => {
    calls++; return { ok: false, status: 429, json: async () => ({ detail: "Quota exceeded. Try later." }) };
  }));
  await assert.rejects(flow.answer("d1", "Question?"), /Quota exceeded/);
  assert.equal(calls, 1); assert.equal(flow.answers.size, 0); assert.equal(flow.busy, false);
  await assert.rejects(flow.answer("d1", "Question?")); assert.equal(calls, 2);
});

test("index creation invalidates older cached answers only for that document", async () => {
  let calls = 0;
  const flow = new DocumentFlow({ index: async () => index,
    answer: async (id, query) => { calls++; return answer(id, query); } });
  await flow.answer("d1", "Question?"); await flow.answer("d2", "Question?"); await flow.index("d1");
  assert.equal((await flow.answer("d1", "Question?")).cached, false);
  assert.equal((await flow.answer("d2", "Question?")).cached, true); assert.equal(calls, 3);
});

test("answer cache is bounded and evicts older entries", async () => {
  const flow = new DocumentFlow({ answer: async (id, query) => answer(id, query) });
  for (let i = 0; i < 21; i++) await flow.answer("d1", `Question ${i}?`);
  assert.equal(flow.answers.size, 20);
  assert.equal((await flow.answer("d1", "Question 0?")).cached, false);
});
