import test from "node:test";
import assert from "node:assert/strict";
import {
  ResumeClient,
  validateProfile,
} from "../src/main/resources/static/resume-client.js";
import { InterviewFlow } from "../src/main/resources/static/interview-client.js";
const profile = {
  id: "r1",
  filename: "resume.docx",
  skills: [{ name: "Python", evidence: "Built tools with Python." }],
};
const ok = (data) => ({ ok: true, json: async () => data });
test("paste uses actual resume endpoint and returns evidence without a preset skill list", async () => {
  let call;
  const client = new ResumeClient(async (path, options) => {
    call = { path, options };
    return ok(profile);
  });
  assert.deepEqual(await client.paste("Built tools with Python."), profile);
  assert.equal(call.path, "/resumes/text");
  assert.deepEqual(JSON.parse(call.options.body), {
    text: "Built tools with Python.",
  });
});
test("upload sends multipart without manually overriding the browser boundary", async () => {
  let request;
  const file = new File(["Python"], "resume.txt", { type: "text/plain" });
  await new ResumeClient(async (path, options) => {
    request = { path, options };
    return ok(profile);
  }).upload(file);
  assert.equal(request.path, "/resumes");
  assert.equal(request.options.body.get("file").name, "resume.txt");
  assert.equal(request.options.headers["Content-Type"], undefined);
});
test("invalid files/text and failed extraction are rejected without invented skills", async () => {
  let calls = 0;
  const client = new ResumeClient(async () => {
    calls++;
    return {
      ok: false,
      json: async () => ({ detail: "Extraction unavailable." }),
    };
  });
  await assert.rejects(
    () => client.upload(new File(["pdf"], "resume.pdf")),
    /PDF/,
  );
  await assert.rejects(() => client.paste("  "));
  assert.equal(calls, 0);
  await assert.rejects(() => client.paste("Python"), /Extraction unavailable/);
  assert.deepEqual(validateProfile({ ...profile, skills: [] }).skills, []);
  assert.throws(() =>
    validateProfile({ ...profile, skills: [{ name: "Python" }] }),
  );
});
test("confirmed skills create a resume session and persist its ID before question generation", async () => {
  const calls = [];
  let saved = false;
  const flow = new InterviewFlow(async (path, options) => {
    calls.push([path, options]);
    if (path === "/resumes/r1/sessions")
      return ok({
        id: "s1",
        status: "ACTIVE",
        resume: { resumeId: "r1", skills: profile.skills },
      });
    assert.equal(saved, true);
    return ok({
      id: "q1",
      sessionId: "s1",
      question: "Explain Python generators.",
      skill: "Python",
    });
  });
  await flow.startFromResume(
    "r1",
    { skills: ["Python"], level: "beginner" },
    undefined,
    () => (saved = true),
  );
  assert.equal(calls[0][0], "/resumes/r1/sessions");
  assert.deepEqual(JSON.parse(calls[0][1].body), {
    skills: ["Python"],
    level: "beginner",
  });
  assert.equal(flow.question.skill, "Python");
  assert.ok(!calls.some(([p]) => p === "/interview/options"));
});
test("failed first resume question retains saved session for retry and does not re-extract or recreate", async () => {
  let created = 0,
    fail = true;
  const flow = new InterviewFlow(async (path) => {
    if (path.endsWith("/sessions")) {
      created++;
      return ok({ id: "s1", status: "ACTIVE" });
    }
    if (fail) throw new Error("Provider down");
    return ok({
      id: "q1",
      sessionId: "s1",
      question: "Explain SQL isolation.",
      skill: "SQL",
    });
  });
  await assert.rejects(
    () => flow.startFromResume("r1", { skills: ["SQL"], level: "advanced" }),
    /Provider down/,
  );
  assert.equal(flow.session.id, "s1");
  fail = false;
  await flow.nextQuestion();
  assert.equal(created, 1);
  assert.equal(flow.question.skill, "SQL");
});
