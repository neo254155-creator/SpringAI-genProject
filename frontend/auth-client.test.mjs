import test from "node:test";
import assert from "node:assert/strict";
import { createAppFetch, safeNext } from "../src/main/resources/static/ui/auth-client.js";
const token = { headerName: "X-CSRF-TOKEN", token: "fixture-token" };
const ok = value => ({ ok: true, status: 200, json: async () => value });

test("mutation requests share a CSRF lookup and send session cookies without changing the body", async () => {
  const calls = [];
  const fetcher = createAppFetch(async (url, options) => { calls.push({ url, ...options }); return url.endsWith("/auth/csrf") ? ok(token) : ok({}); });
  await Promise.all([fetcher("/resumes/text", { method: "POST", body: "private resume", headers: { "Idempotency-Key": "fixture" } }),
    fetcher("/interview/sessions", { method: "POST", body: "selection" })]);
  assert.equal(calls.filter(c => c.url.endsWith("/auth/csrf")).length, 1);
  for (const call of calls) assert.equal(call.credentials, "same-origin");
  assert.equal(calls[1].headers["X-CSRF-TOKEN"], "fixture-token");
  assert.equal(calls[1].headers["Idempotency-Key"], "fixture"); assert.equal(calls[1].body, "private resume");
});

test("reads need no CSRF lookup and anonymous responses redirect once without retrying", async () => {
  let requests = 0, redirects = 0;
  const fetcher = createAppFetch(async () => { requests++; return { ok: false, status: 401 }; }, () => redirects++);
  await fetcher("/interview/progress"); assert.equal(requests, 1); assert.equal(redirects, 1);
  await fetcher("/auth/login", { method: "GET" }); assert.equal(redirects, 1);
});

test("expired CSRF responses do not retry the mutation; the next explicit attempt gets a fresh token", async () => {
  let tokens = 0, mutations = 0;
  const fetcher = createAppFetch(async url => {
    if (url.endsWith("/auth/csrf")) { tokens++; return ok({ ...token, token: `token-${tokens}` }); }
    mutations++; return { ok: false, status: 403 };
  });
  await fetcher("/action", { method: "POST" }); assert.equal(tokens, 1); assert.equal(mutations, 1);
  await fetcher("/action", { method: "POST" }); assert.equal(tokens, 2); assert.equal(mutations, 2);
});

test("login and logout can discard stale CSRF tokens without persisting credentials", async () => {
  let tokens = 0;
  const fetcher = createAppFetch(async url => url.endsWith("/auth/csrf") ? (tokens++, ok(token)) : ok({}));
  await fetcher("/action", { method: "POST" }); fetcher.resetCsrf();
  await fetcher("/action", { method: "POST" }); assert.equal(tokens, 2);
});

test("unsafe or malformed CSRF metadata is rejected before a mutation", async () => {
  let actions = 0;
  const fetcher = createAppFetch(async url => {
    if (url.endsWith("/auth/csrf")) return ok({ headerName: "Cookie", token: "unsafe" });
    actions++; return ok({});
  });
  await assert.rejects(fetcher("/action", { method: "POST" }), /invalid session token/); assert.equal(actions, 0);
});

test("sign-in return paths stay on supported local account pages", () => {
  for (const value of ["https://evil.test/practice.html", "//evil.test", "javascript:alert(1)", "/auth/logout", "/login.html", "/%2f%2fevil.test"])
    assert.equal(safeNext(value), "/practice.html");
  assert.equal(safeNext("/practice.html?session=s1"), "/practice.html?session=s1");
});

test("legacy GET chat also gets CSRF protection because it can consume provider quota", async () => {
  let protectedRequest = false;
  const fetcher = createAppFetch(async (url, options) => {
    if (url.endsWith("/auth/csrf")) return ok(token);
    protectedRequest = options.headers["X-CSRF-TOKEN"] === token.token; return ok({});
  });
  await fetcher("/chat?q=Python"); assert.equal(protectedRequest, true);
});

 test("cancelling one caller during shared CSRF setup never sends its mutation", async () => {
  let release; let mutations = 0, csrfRequests = 0;
  const pending = new Promise(resolve => release = resolve);
  const fetcher = createAppFetch(async url => {
    if(url.endsWith("/auth/csrf")) { csrfRequests++; await pending; return ok(token); }
    mutations++; return ok({});
  });
  const controller = new AbortController();
  const cancelled = fetcher("/resumes/text", {method:"POST", signal:controller.signal});
  const surviving = fetcher("/interview/sessions", {method:"POST"});
  controller.abort();
  await assert.rejects(cancelled, {name:"AbortError"});
  assert.equal(mutations,0);release();await surviving;
  assert.equal(csrfRequests,1);assert.equal(mutations,1);
 });
