import test from "node:test";
import assert from "node:assert/strict";
import { buildAnalytics } from "../src/main/resources/static/ui/analytics.js";
import { DashboardClient } from "../src/main/resources/static/ui/dashboard-client.js";
import {
  configureApi,
  apiUrl,
} from "../src/main/resources/static/ui/api-config.js";
import {
  escape,
  lineChart,
} from "../src/main/resources/static/ui/components.js";
const response = (value) => ({ ok: true, json: async () => value });
const row = (topic, level, attempts, averageScore) => ({
  track: "Java",
  topic,
  level,
  attempts,
  averageScore,
  questionsAnswered: attempts,
  bestScore: 10,
});

test("analytics weights attempts, compares chronological actual answers, and ranks weaker topics first", () => {
  const a = buildAnalytics(
    [
      row("Core Java", "beginner", 1, 10),
      row("Core Java", "advanced", 3, 6),
      row("Spring Boot", "intermediate", 2, 4),
    ],
    [],
    [
      {
        session: { topic: "Spring Boot" },
        answer: { submittedAt: "2026-10-26T12:00:00Z", feedback: { score: 8 } },
      },
      {
        session: { topic: "Core Java" },
        answer: { submittedAt: "2026-10-24T12:00:00Z", feedback: { score: 4 } },
      },
      { session: { topic: "Core Java" }, answer: null },
    ],
  );
  assert.equal(a.skills.find((s) => s.topic === "Core Java").score, 7);
  assert.equal(a.totals.averageScore, 6);
  assert.equal(a.trend, 4);
  assert.deepEqual(
    a.points.map((p) => p.score),
    [4, 8],
  );
  assert.equal(a.recommendations[0].topic, "Spring Boot");
});
test("empty and single-answer analytics never invent readiness, improvement, or unknown skill scores", () => {
  const a = buildAnalytics([], [], []);
  assert.equal(a.trend, null);
  assert.equal(a.totals.averageScore, null);
  assert.deepEqual(a.skills, []);
  assert.deepEqual(a.recommendations, []);
  assert.equal(a.days.length, 7);
  assert.ok(a.days.every((d) => d.count === 0));
  assert.equal(
    buildAnalytics(
      [],
      [],
      [
        {
          session: { topic: "Core Java" },
          answer: { submittedAt: "2026-10-26", feedback: { score: 5 } },
        },
      ],
    ).trend,
    null,
  );
});
test("weekly activity counts only supplied sessions inside seven local calendar days", () => {
  const now = new Date(2026, 9, 26, 12),
    sessions = [
      { createdAt: new Date(2026, 9, 26, 10).toISOString() },
      { createdAt: new Date(2026, 9, 20, 12).toISOString() },
      { createdAt: new Date(2026, 9, 19, 12).toISOString() },
    ];
  const a = buildAnalytics([], sessions, [], now);
  assert.equal(a.days[0].count, 1);
  assert.equal(a.days[6].count, 1);
  assert.equal(
    a.days.reduce((n, d) => n + d.count, 0),
    2,
  );
});
test("dashboard uses existing routes and exposes partial detail failures without fabricated values", async () => {
  const paths = [];
  const fetcher = async (path) => {
    paths.push(path);
    if (path === "/interview/progress") return response([]);
    if (path.startsWith("/interview/sessions?"))
      return response({
        content: [{ id: "a" }, { id: "b" }],
        page: 0,
        totalPages: 1,
        totalElements: 2,
      });
    if (path.endsWith("/a/resume"))
      return response({ session: { id: "a" }, answer: null });
    throw new Error("Unavailable");
  };
  const data = await new DashboardClient(fetcher).load();
  assert.equal(data.partial, true);
  assert.equal(data.resumes.length, 1);
  assert.equal(data.page.totalElements, 2);
  assert.deepEqual(
    paths.sort(),
    [
      "/interview/progress",
      "/interview/sessions?page=0&size=10",
      "/interview/sessions/a/resume",
      "/interview/sessions/b/resume",
    ].sort(),
  );
});
test("dashboard propagates failed summary reads and invalid scores", async () => {
  await assert.rejects(
    () =>
      new DashboardClient(async () => {
        throw new Error("Offline");
      }).load(),
    /Offline/,
  );
  const fetcher = async (path) =>
    response(
      path === "/interview/progress"
        ? []
        : path.includes("?")
          ? { content: [{ id: "a" }], page: 0, totalPages: 1, totalElements: 1 }
          : { session: { id: "a" }, answer: { feedback: { score: 99 } } },
    );
  await assert.rejects(
    () => new DashboardClient(fetcher).load(),
    /invalid saved-session/,
  );
});
test("configured public API prefix applies centrally and credential-bearing URLs are rejected", async () => {
  try {
    await configureApi(async () =>
      response({ apiBaseUrl: "https://api.example.com/base/" }),
    );
    assert.equal(
      apiUrl("/interview/options"),
      "https://api.example.com/base/interview/options",
    );
    for (const value of [
      "javascript:alert(1)",
      "https://user:password@example.com",
      "https://example.com?token=private",
      "https://example.com#fragment",
    ])
      await assert.rejects(() =>
        configureApi(async () => response({ apiBaseUrl: value })),
      );
    await assert.rejects(
      () => configureApi(async () => ({ ok: false })),
      /configuration/,
    );
  } finally {
    await configureApi(async () => response({ apiBaseUrl: "" }));
  }
  assert.equal(apiUrl("/chat"), "/chat");
});
test("chart strings escape backend text and include an accessible data table", () => {
  const chart = lineChart([
    { score: 6, label: "<img src=x onerror=alert(1)>", short: "<b>" },
  ]);
  assert.ok(!chart.includes("<img"));
  assert.ok(chart.includes("&lt;img"));
  assert.ok(chart.includes("View chart data"));
  assert.ok(chart.includes('tabindex="0"'));
  assert.equal(escape('"<&'), "&quot;&lt;&amp;");
});

test("dashboard cancellation is an aborted operation, not an empty or partial success", async () => {
  const controller = new AbortController();
  const client = new DashboardClient(async () => {
    throw new Error("Fetch must not run");
  });
  controller.abort();
  await assert.rejects(() => client.load(controller.signal), {
    name: "AbortError",
  });
});
