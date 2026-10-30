import { requestJson, readJsonResponse } from "./interview-client.js";
import { apiUrl } from "./ui/api-config.js";
export function validateProfile(profile) {
  if (
    !profile?.id ||
    typeof profile.filename !== "string" ||
    !Array.isArray(profile.skills) ||
    profile.skills.length > 12 ||
    !profile.skills.every(
      (s) =>
        typeof s.name === "string" &&
        s.name.trim() &&
        typeof s.evidence === "string" &&
        s.evidence.trim(),
    )
  )
    throw new Error(
      "The server returned invalid resume skills. Please try extraction again.",
    );
  return profile;
}
export class ResumeClient {
  constructor(fetcher = globalThis.fetch) {
    this.fetcher = fetcher;
  }
  async paste(text, signal) {
    if (typeof text !== "string" || !text.trim() || text.trim().length > 24000)
      throw new Error(
        "Paste between 1 and 24,000 characters of readable resume text.",
      );
    return validateProfile(
      await requestJson("/resumes/text", {
        method: "POST",
        body: { text },
        signal,
        fetcher: this.fetcher,
      }),
    );
  }
  async upload(file, signal) {
    if (
      !file ||
      !file.size ||
      file.size > 1048576 ||
      !/\.(docx|txt|md)$/i.test(file.name)
    )
      throw new Error(
        "Choose a nonempty DOCX/TXT/MD file up to 1 MiB. For PDF, paste its text instead.",
      );
    const form = new FormData();
    form.append("file", file);
    return validateProfile(
      await readJsonResponse(
        await this.fetcher(apiUrl("/resumes"), {
          method: "POST",
          body: form,
          signal,
          headers: { Accept: "application/json" },
        }),
      ),
    );
  }
  async get(id, signal) {
    return validateProfile(
      await requestJson(`/resumes/${encodeURIComponent(id)}`, {
        signal,
        fetcher: this.fetcher,
      }),
    );
  }
}
