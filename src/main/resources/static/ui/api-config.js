let base = "";
export function apiUrl(path) {
  return base + path;
}
export async function configureApi(fetcher = globalThis.fetch) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  let response;
  try {
    response = await fetcher("/frontend-config", {
      headers: { Accept: "application/json" },
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timer);
  }
  if (!response.ok)
    throw new Error(
      "Could not load application configuration. Run this site through Spring Boot and try again.",
    );
  const config = await response.json();
  if (typeof config.apiBaseUrl !== "string")
    throw new Error("Invalid public API configuration.");
  const value = config.apiBaseUrl.trim().replace(/\/+$/, "");
  if (value) {
    const url = new URL(value);
    if (
      !["http:", "https:"].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    ) {
      throw new Error(
        "The public API URL must be an HTTP(S) URL without credentials, query, or fragment.",
      );
    }
  }
  base = value;
}
