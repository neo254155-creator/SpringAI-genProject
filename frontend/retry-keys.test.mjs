import test from "node:test";
import assert from "node:assert/strict";
import { RetryKeys, RETRY_STORAGE_KEY } from "../src/main/resources/static/ui/retry-keys.js";
import { requestJson, InterviewFlow } from "../src/main/resources/static/interview-client.js";

const storage = () => { const values = new Map(); return {
  getItem: name => values.get(name) ?? null, setItem: (name, value) => values.set(name, value),
}; };
const response = (body, { ok = true, state } = {}) => ({ ok, status: ok ? 201 : 502,
  headers: { get: name => name === "Idempotency-Status" ? state : null }, json: async () => body });

test("pending keys survive reload without saving resume text, answers, or request paths", async () => {
  const saved = storage(), first = new RetryKeys(saved);
  const a = await first.begin("/resumes/text", "private resume evidence");
  const b = await new RetryKeys(saved).begin("/resumes/text", "private resume evidence");
  assert.equal(a.key, b.key);
  const raw = saved.getItem(RETRY_STORAGE_KEY);
  assert.ok(!raw.includes("private") && !raw.includes("/resumes"));
  assert.notEqual((await first.begin("/resumes/text", "changed input")).key, a.key);
  assert.notEqual((await first.begin("/other", "private resume evidence")).key, a.key);
});

test("a confirmed request finishes its key; later identical actions can be intentional new attempts", async () => {
  const keys = new RetryKeys(storage()); const a = await keys.begin("/same", "body"); a.finish();
  assert.notEqual((await keys.begin("/same", "body")).key, a.key);
});

test("transport and unreadable-response failures keep the key for a manual retry", async () => {
  const keys = new RetryKeys(storage()), seen = [];
  await assert.rejects(requestJson("/resumes/text", { method: "POST", body: { text: "Python" }, keys,
    fetcher: async (url, options) => { seen.push(options.headers["Idempotency-Key"]); throw new TypeError("network lost"); } }));
  await assert.rejects(requestJson("/resumes/text", { method: "POST", body: { text: "Python" }, keys,
    fetcher: async (url, options) => { seen.push(options.headers["Idempotency-Key"]);
      return { ok: true, status: 201, json: async () => { throw new Error("truncated"); } }; } }));
  await requestJson("/resumes/text", { method: "POST", body: { text: "Python" }, keys,
    fetcher: async (url, options) => { seen.push(options.headers["Idempotency-Key"]); return response({ id: "r1" }); } });
  assert.equal(new Set(seen).size, 1);
});

test("invalid success data keeps its key until the response passes validation", async () => {
  const keys = new RetryKeys(storage()), seen = [];
  const validate = value => { if (!value.id) throw new Error("invalid profile"); return value; };
  const fetcher = async (url, options) => { seen.push(options.headers["Idempotency-Key"]); return response(seen.length === 1 ? {} : { id: "r1" }); };
  await assert.rejects(requestJson("/resumes", { method: "POST", keys, fetcher, validate }));
  await requestJson("/resumes", { method: "POST", keys, fetcher, validate });
  assert.equal(seen[0], seen[1]);
});

test("only a definitive server failure releases a key for the next explicit retry", async () => {
  const keys = new RetryKeys(storage()), seen = [];
  for (const state of ["pending", "failed", "failed"]) {
    await assert.rejects(requestJson("/questions", { method: "POST", keys,
      fetcher: async (url, options) => { seen.push(options.headers["Idempotency-Key"]); return response({ detail: "Try later" }, { ok: false, state }); } }));
  }
  assert.equal(seen[0], seen[1]); assert.notEqual(seen[1], seen[2]);
});

test("multipart identity uses file bytes and metadata, independent of generated boundaries", async () => {
  const keys = new RetryKeys(storage());
  const form = content => { const data = new FormData(); data.append("file", new File([content], "resume.txt", { type: "text/plain" })); return data; };
  const first = await keys.begin("/resumes", form("Python"));
  assert.equal((await keys.begin("/resumes", form("Python"))).key, first.key);
  assert.notEqual((await keys.begin("/resumes", form("Java"))).key, first.key);
});

test("simultaneous key acquisition shares one key and old responses cannot clear a newer attempt", async () => {
  const keys = new RetryKeys(storage());
  const [a, b] = await Promise.all([keys.begin("/route", "body"), keys.begin("/route", "body")]);
  assert.equal(a.key, b.key); a.finish(); const next = await keys.begin("/route", "body"); b.finish();
  assert.equal((await keys.begin("/route", "body")).key, next.key);
});

test("lost session creation responses reuse the key across workflow instances", async () => {
  let created = 0, questionCalls = 0; const saved = new Map();
  const selection = { track: "Java", topic: "Core Java", level: "advanced" };
  const fetcher = async (url, options) => {
    if (url === "/interview/sessions") {
      const key = options.headers["Idempotency-Key"];
      if (!saved.has(key)) { created++; saved.set(key, { id: "lost-session", status: "ACTIVE", ...selection }); throw new TypeError("response lost after commit"); }
      return response(saved.get(key));
    }
    questionCalls++; return response({ id: "q1", sessionId: "lost-session", question: "Explain an interface." });
  };
  await assert.rejects(new InterviewFlow(fetcher).start(selection));
  const retry = new InterviewFlow(fetcher); await retry.start(selection);
  assert.equal(created, 1); assert.equal(questionCalls, 1); assert.equal(retry.session.id, "lost-session");
});

test("read requests never acquire a mutation key", async () => {
  await requestJson("/interview/progress", { keys: { begin: () => assert.fail("read must not acquire key") },
    fetcher: async (url, options) => { assert.equal(options.headers["Idempotency-Key"], undefined); return response([]); } });
});

test("opening saved progress reconciles a lost question response before asking a new question", async () => {
  const keys = new RetryKeys(storage()), questionKeys = [];
  const session = { id: "recovered", status: "ACTIVE", track: "Java", topic: "Core Java", level: "beginner" };
  const question = { id: "q1", sessionId: session.id, question: "Explain an interface." };
  const feedback = { score: 10, maxScore: 10, explanation: "Clear.", correctPoints: [], missingPoints: [],
    rubric: { correctness: { score: 5, reason: "Accurate." }, completeness: { score: 3, reason: "Complete." }, clarity: { score: 2, reason: "Clear." } } };
  const fetcher = async (url, options) => {
    if (url === "/interview/sessions") return response(session);
    if (url.endsWith("/resume")) {
      assert.equal(options.headers["Idempotency-Key"], questionKeys[0]);
      return response({ session, question, answer: null, questionsAnswered: 0 }, { state: "completed" });
    }
    if (url.endsWith("/answers")) return response({ questionId: question.id, feedback });
    questionKeys.push(options.headers["Idempotency-Key"]);
    if (questionKeys.length === 1) throw new TypeError("Question response was lost after saving");
    return response({ ...question, id: "q2", question: "Explain an abstract class." });
  };
  await assert.rejects(new InterviewFlow(fetcher, keys).start(session));
  const reopened = new InterviewFlow(fetcher, keys); await reopened.resume(session.id);
  await reopened.submit("An interface defines a contract."); await reopened.nextQuestion();
  assert.notEqual(questionKeys[0], questionKeys[1]); assert.equal(reopened.question.id, "q2");
});

test("unconfirmed generation stays blocked after a saved-session read", async () => {
  const keys = new RetryKeys(storage()), pending = await keys.begin("/interview/sessions/pending/questions");
  const flow = new InterviewFlow(async (url, options) => {
    assert.equal(options.headers["Idempotency-Key"], pending.key);
    if (url.endsWith("/resume")) return response({ session: { id: "pending", status: "ACTIVE" },
      question: null, answer: null, questionsAnswered: 0 }, { state: "pending" });
    return response({ detail: "Request is still pending." }, { ok: false, state: "pending" });
  }, keys);
  await flow.resume("pending"); await assert.rejects(flow.nextQuestion(), /still pending/);
  assert.equal((await keys.lookup("/interview/sessions/pending/questions")).key, pending.key);
});
