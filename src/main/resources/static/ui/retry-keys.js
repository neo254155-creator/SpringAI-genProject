export const RETRY_STORAGE_KEY = "intervie.pendingRequests.v1";
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const hashPattern = /^[0-9a-f]{64}$/;
const encoder = new TextEncoder();
async function hash(bytes) {
  if (!globalThis.crypto?.subtle || !globalThis.crypto?.randomUUID)
    throw new Error("Open this app over HTTPS or localhost to continue safely.");
  return [...new Uint8Array(await crypto.subtle.digest("SHA-256", bytes))]
    .map(byte => byte.toString(16).padStart(2, "0")).join("");
}
function defaultStorage() {
  try { return globalThis.sessionStorage; } catch { return undefined; }
}

/** Persist only hashes and random keys, never answers, files, or full resume text. */
export class RetryKeys {
  constructor(storage = defaultStorage()) { this.storage = storage; this.memory = {}; }
  clear() {
    this.memory = {};
    try { this.storage?.removeItem(RETRY_STORAGE_KEY); } catch { this.storage = undefined; }
  }
  #read() {
    let raw;
    try { raw = this.storage?.getItem(RETRY_STORAGE_KEY); }
    catch { this.storage = undefined; }
    if (!raw) return this.memory;
    let entries;
    try { entries = JSON.parse(raw); } catch { throw new Error("Saved retry state could not be read. Review saved progress before continuing."); }
    if (!entries || Array.isArray(entries) || typeof entries !== "object"
        || Object.keys(entries).length > 200 || !Object.entries(entries).every(([key, value]) => hashPattern.test(key) && uuid.test(value)))
      throw new Error("Saved retry state is invalid. Review saved progress before continuing.");
    this.memory = entries;
    return entries;
  }
  #write(entries) {
    this.memory = entries;
    try { this.storage?.setItem(RETRY_STORAGE_KEY, JSON.stringify(entries)); }
    catch { this.storage = undefined; }
  }
  async #fingerprint(path, body) {
    let identity = body ?? "";
    if (body instanceof FormData) {
      const parts = [];
      for (const [name, value] of body.entries()) parts.push(typeof value === "string" ? [name, value]
        : [name, value.name, value.type, await hash(await value.arrayBuffer())]);
      identity = JSON.stringify(parts.sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))));
    }
    return hash(encoder.encode(`POST ${path}\0${identity}`));
  }
  async lookup(path, body) {
    if (!Object.keys(this.#read()).length) return null;
    const fingerprint = await this.#fingerprint(path, body);
    return this.#read()[fingerprint] ? this.#attempt(fingerprint) : null;
  }
  async begin(path, body) {
    const fingerprint = await this.#fingerprint(path, body);
    const entries = this.#read();
    if (!entries[fingerprint]) {
      if (Object.keys(entries).length >= 200)
        throw new Error("Too many unconfirmed requests. Review saved progress before continuing.");
      entries[fingerprint] = crypto.randomUUID(); this.#write(entries);
    }
    return this.#attempt(fingerprint);
  }
  #attempt(fingerprint) {
    const key = this.#read()[fingerprint];
    return { key, finish: () => {
      const current = this.#read();
      if (current[fingerprint] === key) { delete current[fingerprint]; this.#write(current); }
    } };
  }
}
export const retryKeys = new RetryKeys();
