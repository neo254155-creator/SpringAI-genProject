import { requestJson } from './interview-client.js';

const segment = id => encodeURIComponent(id);
export class HistoryClient {
  constructor(fetcher = globalThis.fetch) { this.fetcher = fetcher; }
  async #read(path, signal) { return requestJson(path, { signal, fetcher: this.fetcher }); }
  async #page(path, page, signal) {
    if (!Number.isInteger(page) || page < 0) throw new Error('Invalid page number.');
    const result = await this.#read(`${path}?page=${page}&size=10`, signal);
    if (!Array.isArray(result?.content) || result.page !== page || !Number.isInteger(result.totalPages)
        || result.totalPages < 0 || !Number.isSafeInteger(result.totalElements) || result.totalElements < 0) {
      throw new Error('The server returned invalid history data.');
    }
    return result;
  }
  sessions(page = 0, signal) { return this.#page('/interview/sessions', page, signal); }
  questions(id, page = 0, signal) { return this.#page(`/interview/sessions/${segment(id)}/questions`, page, signal); }
  answers(id, page = 0, signal) { return this.#page(`/interview/questions/${segment(id)}/answers`, page, signal); }
  session(id, signal) { return this.#read(`/interview/sessions/${segment(id)}`, signal); }
  async progress(signal) {
    const rows = await this.#read('/interview/progress', signal);
    if (!Array.isArray(rows) || !rows.every(row => typeof row.topic === 'string'
        && Number.isSafeInteger(row.attempts) && row.attempts > 0 && Number.isSafeInteger(row.questionsAnswered)
        && row.questionsAnswered > 0 && Number.isFinite(row.averageScore) && row.averageScore >= 0 && row.averageScore <= 10
        && Number.isInteger(row.bestScore) && row.bestScore >= 0 && row.bestScore <= 10)) {
      throw new Error('The server returned invalid progress data.');
    }
    return rows;
  }
}

export function summarizeProgress(rows) {
  const attempts = rows.reduce((sum, row) => sum + row.attempts, 0);
  return {
    attempts,
    questionsAnswered: rows.reduce((sum, row) => sum + row.questionsAnswered, 0),
    averageScore: attempts ? rows.reduce((sum, row) => sum + row.averageScore * row.attempts, 0) / attempts : null,
    bestScore: rows.length ? Math.max(...rows.map(row => row.bestScore)) : null,
  };
}
