import test from 'node:test';
import assert from 'node:assert/strict';
import { HistoryClient, summarizeProgress } from '../src/main/resources/static/history-client.js';

const page = (number, content = []) => ({ content, page: number, size: 10, totalElements: 25, totalPages: 3 });
const ok = body => ({ ok: true, status: 200, json: async () => body });

test('history uses paginated session, question and answer routes with cancellation', async () => {
  const calls = [], controller = new AbortController();
  const client = new HistoryClient(async (path, options) => { calls.push({ path, options }); return ok(page(2)); });
  await client.sessions(2, controller.signal);
  await client.questions('session/with?characters', 2, controller.signal);
  await client.answers('question-1', 2, controller.signal);
  assert.deepEqual(calls.map(c => c.path), ['/interview/sessions?page=2&size=10',
    '/interview/sessions/session%2Fwith%3Fcharacters/questions?page=2&size=10', '/interview/questions/question-1/answers?page=2&size=10']);
  assert.ok(calls.every(c => c.options.method === 'GET' && c.options.signal === controller.signal));
});

test('progress average is weighted by attempts and includes actual distinct-question counts', async () => {
  const rows = [
    { track: 'Java', topic: 'Core Java', level: 'beginner', attempts: 1, questionsAnswered: 1, averageScore: 10, bestScore: 10 },
    { track: 'Java', topic: 'Spring Boot', level: 'intermediate', attempts: 9, questionsAnswered: 5, averageScore: 6, bestScore: 9 },
  ];
  const client = new HistoryClient(async path => { assert.equal(path, '/interview/progress'); return ok(rows); });
  assert.deepEqual(summarizeProgress(await client.progress()), { attempts: 10, questionsAnswered: 6, averageScore: 6.4, bestScore: 10 });
  assert.deepEqual(summarizeProgress([]), { attempts: 0, questionsAnswered: 0, averageScore: null, bestScore: null });
});

test('empty history is valid while malformed pages, progress and errors are rejected', async () => {
  const empty = new HistoryClient(async () => ok({ ...page(0), totalPages: 0, totalElements: 0 }));
  assert.deepEqual((await empty.sessions()).content, []);
  await assert.rejects(empty.sessions(-1), /Invalid page/);
  const broken = new HistoryClient(async () => ok({ content: [], page: 0 }));
  await assert.rejects(broken.sessions(), /invalid history/);
  await assert.rejects(broken.progress(), /invalid progress/);
  const failed = new HistoryClient(async () => ({ ok: false, status: 503, json: async () => ({ detail: 'Storage unavailable' }) }));
  await assert.rejects(failed.sessions(), /Storage unavailable/);
});
