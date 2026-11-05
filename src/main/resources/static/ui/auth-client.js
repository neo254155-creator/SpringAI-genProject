import { apiUrl } from "./api-config.js";
import { retryKeys } from "./retry-keys.js";

export function safeNext(value) {
  try {
    const url = new URL(value || "/practice.html", "https://intervie.invalid");
    return url.origin === "https://intervie.invalid"
      && /^\/(?:practice|dashboard|history|documents|analytics|recommendations|questions|settings)\.html$/.test(url.pathname)
      ? url.pathname + url.search : "/practice.html";
  } catch { return "/practice.html"; }
}
export function createAppFetch(fetcher = globalThis.fetch, redirect = () => {
  if (globalThis.location && location.pathname !== "/login.html")
    location.assign("/login.html?next=" + encodeURIComponent(safeNext(location.pathname + location.search)));
}) {
  let csrf, loading;
  async function token() {
    if (csrf) return csrf;
    if (!loading) loading = (async () => {
      const response = await fetcher(apiUrl("/auth/csrf"), { credentials: "same-origin",
        headers: { Accept: "application/json" }, signal: AbortSignal.timeout(10000) });
      if (!response.ok) throw new Error("Could not prepare a secure session. Please try again.");
      const value = await response.json();
      if (!/^X-[A-Za-z0-9-]{1,80}$/.test(value.headerName) || typeof value.token !== "string"
          || !value.token || value.token.length > 4096 || /[\r\n]/.test(value.token))
        throw new Error("The server returned an invalid session token.");
      return csrf = value;
    })().finally(() => { loading = null; });
    return loading;
  }
  const appFetch = async (url, options = {}) => {
    options.signal?.throwIfAborted();
    if (globalThis.location && new URL(url, location.origin).origin !== location.origin)
      throw new Error("Account pages require an API on the same site. Use a same-site server proxy.");
    const method = (options.method || "GET").toUpperCase();
    const headers = { ...options.headers };
    if (!["GET", "HEAD", "OPTIONS"].includes(method) || /\/chat(?:$|\?)/.test(url)) {
      const value = await token(); headers[value.headerName] = value.token;
    }
    const response = await fetcher(url, { ...options, headers, credentials: "same-origin" });
    if (response.status === 403) csrf = null;
    if (response.status === 401 && !/\/auth\/(?:login|register|csrf)(?:$|\?)/.test(url)) redirect();
    return response;
  };
  appFetch.resetCsrf = () => { csrf = null; loading = null; };
  return appFetch;
}
export const appFetch = createAppFetch();
async function read(response) {
  let data;
  try { data = await response.json(); } catch { throw new Error("The server returned an unreadable account response."); }
  if (!response.ok) throw new Error(data.detail || "The account request failed.");
  if (typeof data.id !== "string" || typeof data.username !== "string") throw new Error("The server returned an invalid account.");
  return data;
}
export const currentAccount = () => appFetch(apiUrl("/auth/me"), { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(10000) }).then(read);
export async function accountAction(action, credentials) {
  const result = await read(await appFetch(apiUrl(`/auth/${action}`), { method: "POST", signal: AbortSignal.timeout(20000),
    headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify(credentials) }));
  if (action === "login") { retryKeys.clear(); appFetch.resetCsrf(); }
  return result;
}
export async function signOut() {
  const response = await appFetch(apiUrl("/auth/logout"), { method: "POST", signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error("Could not confirm sign-out. Please try again.");
  retryKeys.clear(); appFetch.resetCsrf(); location.assign("/login.html");
}
