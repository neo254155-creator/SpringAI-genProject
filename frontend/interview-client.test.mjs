import test from 'node:test';
import assert from 'node:assert/strict';
import { InterviewFlow, requestJson } from '../src/main/resources/static/interview-client.js';

const selection = { track: 'Java', topic: 'Spring Boot', level: 'intermediate' };
const session = { id: 's1', status: 'ACTIVE', ...selection };
const question = { id: 'q1', sessionId: 's1', question: 'What is dependency injection?' };
const feedback = {
  score: 7, maxScore: 10, correctPoints: ['Constructor injection'], missingPoints: ['Lifecycle'], explanation: 'Review bean lifecycle.',
  rubric: { correctness: { score: 4, reason: 'Mostly correct' }, completeness: { score: 2, reason: 'One gap' }, clarity: { score: 1, reason: 'Add detail' } },
};
const savedAnswer = { questionId: 'q1', feedback };
const ok = body => ({ ok: true, status: 200, json: async () => body });
const fail = detail => ({ ok: false, status: 502, json: async () => ({ detail }) });
function scripted(responses) {
  const calls = [];
  return { calls, fetcher: async (path, options) => {
    calls.push({ path, ...options });
    assert.ok(responses.length, 'Unexpected request');
    const response = responses.shift();
    if (response instanceof Error) throw response;
    return response;
  } };
}

test('complete saved-session flow uses the right IDs and resets for the next question', async () => {
  const api = scripted([ok(session), ok(question), ok(savedAnswer), ok({ ...question, id: 'q2' }), ok({ ...session, status: 'COMPLETED' })]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.start(selection);
  await flow.submit('I use constructor injection.');
  assert.deepEqual(flow.feedback, feedback);
  assert.equal(flow.answered, 1);
  await flow.nextQuestion();
  assert.equal(flow.question.id, 'q2');
  assert.equal(flow.feedback, null);
  await flow.complete();
  assert.equal(flow.session.status, 'COMPLETED');
  assert.deepEqual(api.calls.map(c => c.path), [
    '/interview/sessions', '/interview/sessions/s1/questions', '/interview/questions/q1/answers',
    '/interview/sessions/s1/questions', '/interview/sessions/s1/complete',
  ]);
  assert.deepEqual(JSON.parse(api.calls[2].body), { answer: 'I use constructor injection.' });
  flow.reset();
  assert.equal(flow.session, null);
  assert.equal(flow.answered, 0);
});

test('failed first question retains session for retry without creating another session', async () => {
  const api = scripted([ok(session), fail('AI unavailable'), ok(question)]);
  const flow = new InterviewFlow(api.fetcher);
  await assert.rejects(flow.start(selection), /AI unavailable/);
  assert.equal(flow.session.id, 's1');
  assert.equal(flow.busy, false);
  await flow.nextQuestion();
  assert.equal(api.calls.filter(c => c.path === '/interview/sessions').length, 1);
});

test('feedback failure keeps the question answerable; next-question failure keeps feedback', async () => {
  const api = scripted([ok(session), ok(question), fail('Try later'), ok(savedAnswer), fail('Question failed')]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.start(selection);
  await assert.rejects(flow.submit('my answer'), /Try later/);
  assert.equal(flow.question.id, 'q1');
  assert.equal(flow.feedback, null);
  await flow.submit('my answer');
  await assert.rejects(flow.nextQuestion(), /Question failed/);
  assert.deepEqual(flow.feedback, feedback);
  assert.equal(flow.question.id, 'q1');
});

test('rejects invalid answers and premature next-question requests without sending them', async () => {
  const api = scripted([ok(session), ok(question)]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.start(selection);
  await assert.rejects(flow.submit(' '), /Enter an answer/);
  await assert.rejects(flow.submit('x'.repeat(8001)), /Enter an answer/);
  await assert.rejects(flow.nextQuestion(), /Submit your answer/);
  assert.equal(api.calls.length, 2);
});

test('prevents duplicate in-flight submissions and repeat submission after feedback', async () => {
  let release;
  const api = scripted([ok(session), ok(question)]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.start(selection);
  flow.fetcher = () => new Promise(resolve => { release = resolve; });
  const pending = flow.submit('answer');
  await assert.rejects(flow.submit('answer'), /Please wait/);
  release(ok(savedAnswer));
  await pending;
  await assert.rejects(flow.submit('answer'), /no unanswered question/);
});

test('forwards cancellation signal and releases busy state on transport failure', async () => {
  const controller = new AbortController();
  const api = scripted([ok(session), ok(question), new DOMException('Cancelled', 'AbortError')]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.start(selection);
  await assert.rejects(flow.submit('answer', controller.signal), /Cancelled/);
  assert.equal(api.calls[2].signal, controller.signal);
  assert.equal(flow.busy, false);
  assert.equal(flow.feedback, null);
});

test('rejects malformed feedback and unreadable server responses', async () => {
  const api = scripted([ok(session), ok(question), ok({ questionId: 'q1', feedback: { score: 7 } })]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.start(selection);
  await assert.rejects(flow.submit('answer'), /valid feedback/);
  assert.equal(flow.feedback, null);
  await assert.rejects(requestJson('/test', { fetcher: async () => ({ status: 503, json: async () => { throw new Error('HTML'); } }) }), /unreadable response/);
});

test('resume restores saved answer, feedback and distinct count using only a read request', async () => {
  const api = scripted([ok({ session, question, answer: { ...savedAnswer, answer: 'My saved answer' }, questionsAnswered: 4 })]);
  const flow = new InterviewFlow(api.fetcher);
  await flow.resume('s1');
  assert.equal(flow.session.id, 's1');
  assert.equal(flow.question.id, 'q1');
  assert.equal(flow.savedAnswer, 'My saved answer');
  assert.deepEqual(flow.feedback, feedback);
  assert.equal(flow.answered, 4);
  assert.equal(api.calls.length, 1);
  assert.equal(api.calls[0].path, '/interview/sessions/s1/resume');
  assert.equal(api.calls[0].method, 'GET');
  await assert.rejects(flow.submit('duplicate'), /no unanswered question/);
});

test('resume supports empty, unanswered and completed sessions without generating a question', async () => {
  for (const saved of [
    { session, question: null, answer: null, questionsAnswered: 0 },
    { session, question, answer: null, questionsAnswered: 0 },
    { session: { ...session, status: 'COMPLETED' }, question, answer: { ...savedAnswer, answer: 'done' }, questionsAnswered: 1 },
  ]) {
    const api = scripted([ok(saved)]), flow = new InterviewFlow(api.fetcher);
    await flow.resume('s1');
    assert.equal(api.calls.length, 1);
    assert.equal(flow.question, saved.question);
    if (flow.session.status === 'COMPLETED') await assert.rejects(flow.nextQuestion(), /Start an interview/);
  }
});

test('failed or invalid resume leaves the current state unchanged and can be retried', async () => {
  const api = scripted([fail('Session unavailable'), ok({ session: { ...session, id: 'other' }, questionsAnswered: 0 }),
    ok({ session, question: null, answer: null, questionsAnswered: 0 })]);
  const flow = new InterviewFlow(api.fetcher);
  await assert.rejects(flow.resume('s1'), /Session unavailable/);
  assert.equal(flow.session, null);
  await assert.rejects(flow.resume('s1'), /valid saved session/);
  assert.equal(flow.session, null);
  await flow.resume('s1');
  assert.equal(flow.session.id, 's1');
});
