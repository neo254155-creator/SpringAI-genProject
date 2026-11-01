import { requestJson, readJsonResponse } from "../interview-client.js";
import { apiUrl } from "./api-config.js";

const segment = (id) => {
  if (typeof id !== "string" || !id || id.length > 200) throw new Error("Choose a saved document.");
  return encodeURIComponent(id);
};
const hasText = (value) => typeof value === "string" && Boolean(value.trim());
const validDocument = (value) => value && hasText(value.id) && hasText(value.title)
  && hasText(value.filename) && Number.isFinite(Date.parse(value.createdAt));

export function validateUpload(title, file) {
  if (typeof title !== "string" || !title.trim() || title.trim().length > 200 || title.includes("\0"))
    throw new Error("Enter a title between 1 and 200 characters.");
  if (!file || !Number.isSafeInteger(file.size) || file.size < 1 || file.size > 65536
      || typeof file.name !== "string" || !/\.(txt|md)$/i.test(file.name))
    throw new Error("Choose a nonempty UTF-8 TXT or Markdown file up to 64 KiB.");
  return title.trim();
}

export class DocumentsClient {
  constructor(fetcher = globalThis.fetch) { this.fetcher = fetcher; }
  async list(page = 0, signal) {
    if (!Number.isSafeInteger(page) || page < 0) throw new Error("Invalid page number.");
    const result = await requestJson(`/documents?page=${page}&size=10`, { signal, fetcher: this.fetcher });
    if (!Array.isArray(result?.content) || !result.content.every(validDocument) || result.page !== page
        || !Number.isSafeInteger(result.totalElements) || result.totalElements < 0
        || !Number.isSafeInteger(result.totalPages) || result.totalPages < 0)
      throw new Error("The server returned invalid document data.");
    return result;
  }
  async get(id, signal) {
    const result = await requestJson(`/documents/${segment(id)}`, { signal, fetcher: this.fetcher });
    if (!validDocument(result) || result.id !== id) throw new Error("The server returned an invalid document.");
    return result;
  }
  async upload(title, file, signal) {
    title = validateUpload(title, file);
    const body = new FormData();
    body.append("title", title);
    body.append("file", file);
    const fetcher = this.fetcher;
    const result = await readJsonResponse(await fetcher(apiUrl("/documents"), {
      method: "POST", headers: { Accept: "application/json" }, body, signal,
    }));
    if (!validDocument(result)) throw new Error("The server did not return a saved document.");
    return result;
  }
  async index(id, signal) {
    const result = await requestJson(`/documents/${segment(id)}/index`, {
      method: "POST", signal, fetcher: this.fetcher,
    });
    if (result?.documentId !== id || !hasText(result.model) || !Number.isInteger(result.dimensions)
        || result.dimensions < 1 || result.dimensions > 8192 || !Number.isInteger(result.passageCount)
        || result.passageCount < 1)
      throw new Error("The server did not confirm the document index.");
    return result;
  }
  async answer(id, query, signal) {
    if (typeof query !== "string" || !query.trim() || query.trim().length > 2000 || query.includes("\0"))
      throw new Error("Enter a question between 1 and 2,000 characters.");
    query = query.trim();
    const result = await requestJson(`/documents/${segment(id)}/answer`, {
      method: "POST", body: { query, limit: 5 }, signal, fetcher: this.fetcher,
    });
    const sources = result?.sources;
    if (result?.documentId !== id || result.query !== query || !hasText(result.answer)
        || typeof result.insufficientEvidence !== "boolean" || !Array.isArray(sources) || sources.length > 5
        || !sources.every((s) => hasText(s.passageId) && Number.isInteger(s.number) && s.number > 0
          && hasText(s.text) && Number.isFinite(s.similarity) && s.similarity >= -1 && s.similarity <= 1)
        || new Set(sources.map((s) => s.passageId)).size !== sources.length
        || (result.insufficientEvidence ? sources.length !== 0 : sources.length === 0))
      throw new Error("The server returned an invalid cited answer.");
    return result;
  }
}

/** Deduplicate successful paid actions during this visit; never retry automatically. */
export class DocumentFlow {
  indexes = new Map();
  answers = new Map();
  busy = false;
  constructor(client = new DocumentsClient()) { this.client = client; }
  async #run(action) {
    if (this.busy) throw new Error("Please wait for the current request.");
    this.busy = true;
    try { return await action(); } finally { this.busy = false; }
  }
  index(id, signal) {
    return this.#run(async () => {
      if (this.indexes.has(id)) return this.indexes.get(id);
      const result = await this.client.index(id, signal);
      this.indexes.set(id, result);
      // A new index invalidates any earlier answer cached for this document.
      for (const key of this.answers.keys()) if (JSON.parse(key)[0] === id) this.answers.delete(key);
      return result;
    });
  }
  answer(id, query, signal) {
    return this.#run(async () => {
      if (typeof query !== "string") throw new Error("Enter a question.");
      const key = JSON.stringify([id, query.trim()]);
      if (this.answers.has(key)) return { result: this.answers.get(key), cached: true };
      const result = await this.client.answer(id, query, signal);
      if (this.answers.size >= 20) this.answers.delete(this.answers.keys().next().value);
      this.answers.set(key, result);
      return { result, cached: false };
    });
  }
}
