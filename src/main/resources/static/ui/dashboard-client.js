import { HistoryClient } from "../history-client.js";
import { requestJson } from "../interview-client.js";
export class DashboardClient {
  constructor(fetcher = globalThis.fetch) {
    this.fetcher = fetcher;
    this.history = new HistoryClient(fetcher);
  }
  async load(signal) {
    signal?.throwIfAborted();
    const [progress, page] = await Promise.all([
      this.history.progress(signal),
      this.history.sessions(0, signal),
    ]);
    const details = await Promise.allSettled(
      page.content.map((s) =>
        requestJson(`/interview/sessions/${encodeURIComponent(s.id)}/resume`, {
          signal,
          fetcher: this.fetcher,
        }),
      ),
    );
    signal?.throwIfAborted();
    const resumes = details
      .filter((r) => r.status === "fulfilled")
      .map((r) => r.value);
    if (
      !resumes.every(
        (r) =>
          r?.session?.id &&
          (!r.answer ||
            (Number.isInteger(r.answer.feedback?.score) &&
              r.answer.feedback.score >= 0 &&
              r.answer.feedback.score <= 10)),
      )
    )
      throw new Error("The server returned invalid saved-session data.");
    return {
      progress,
      page,
      resumes,
      partial: details.some((r) => r.status === "rejected"),
    };
  }
}
