import { HistoryClient, summarizeProgress } from "./history-client.js";

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function date(value) {
  const parsed = new Date(value);
  return value && Number.isFinite(parsed.getTime())
    ? parsed.toLocaleString()
    : "—";
}

export class InterviewHistory extends HTMLElement {
  #client = new HistoryClient();
  #controllers = new Map();
  #session;
  #question;

  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this.shadowRoot.innerHTML = `
      <link rel="stylesheet" href="/ui/workspace.css">
      <div class="workspace" role="region" aria-label="Practice history">
        <span class="eyebrow">EVERY ANSWER IS A STEP FORWARD</span>
        <h1>Your practice, over time.</h1>
        <p>Review saved interviews, revisit feedback, and continue an unfinished session.</p>
        <p class="hint">This project currently uses one shared learner history. Scores reflect saved answer attempts.</p>
        <section id="progress" aria-labelledby="progress-title">
          <div class="heading"><h2 id="progress-title">Topic progress</h2><button id="refresh-progress" class="secondary">Refresh progress</button></div>
          <p id="progress-status" role="status"></p><div id="progress-body"></div>
        </section>
        <section id="sessions" aria-labelledby="sessions-title">
          <div class="heading"><h2 id="sessions-title">Previous sessions</h2><button id="refresh-sessions" class="secondary">Refresh sessions</button></div>
          <p id="sessions-status" role="status"></p><div id="sessions-body"></div><div id="sessions-pager" class="pager"></div>
        </section>
        <section id="questions" aria-labelledby="questions-title" hidden>
          <h2 id="questions-title" tabindex="-1">Session questions</h2><p id="session-description"></p><div id="session-actions" class="actions"></div>
          <p id="questions-status" role="status"></p><div id="questions-body"></div><div id="questions-pager" class="pager"></div>
        </section>
        <section id="answers" aria-labelledby="answers-title" hidden>
          <h2 id="answers-title" tabindex="-1">Answer attempts & feedback</h2><p id="selected-question"></p>
          <p id="answers-status" role="status"></p><div id="answers-body"></div><div id="answers-pager" class="pager"></div>
        </section>
      </div>`;
    this.#el("refresh-progress").addEventListener("click", () =>
      this.#progress(),
    );
    this.#el("refresh-sessions").addEventListener("click", () =>
      this.#sessions(0),
    );
  }

  #el(id) {
    return this.shadowRoot.getElementById(id);
  }
  connectedCallback() {
    this.dataset.motion = document.documentElement.dataset.motion || "on";
    this.#progress();
    this.#sessions(0);
  }
  disconnectedCallback() {
    for (const controller of this.#controllers.values()) controller.abort();
  }

  async #load(key, action) {
    this.#controllers.get(key)?.abort();
    const controller = new AbortController();
    this.#controllers.set(key, controller);
    const timer = setTimeout(() => controller.abort(), 30000);
    const status = this.#el(`${key}-status`);
    status.className = "";
    status.setAttribute("role", "status");
    status.textContent = "Loading…";
    this.#el(key).setAttribute("aria-busy", "true");
    try {
      await action(controller.signal);
      if (!controller.signal.aborted) status.textContent = "";
    } catch (cause) {
      if (this.#controllers.get(key) !== controller) return;
      status.className = "error";
      status.setAttribute("role", "alert");
      status.textContent = controller.signal.aborted
        ? "The request took too long. Please try again."
        : cause instanceof TypeError
          ? "Could not reach the server. Please try again."
          : cause.message;
      const retry = element("button", "Retry", "secondary");
      retry.addEventListener("click", () => this.#load(key, action));
      status.append(" ", retry);
    } finally {
      clearTimeout(timer);
      if (this.#controllers.get(key) === controller) {
        this.#controllers.delete(key);
        this.#el(key).setAttribute("aria-busy", "false");
      }
    }
  }

  #progress() {
    return this.#load("progress", async (signal) => {
      const rows = await this.#client.progress(signal);
      if (signal.aborted) return;
      const body = this.#el("progress-body");
      body.replaceChildren();
      if (!rows.length) {
        body.append(
          element(
            "p",
            "No scores yet. Submit your first interview answer to see progress.",
          ),
        );
        return;
      }
      const totals = summarizeProgress(rows),
        metrics = element("div", undefined, "metrics");
      for (const [label, value] of [
        ["Answer attempts", totals.attempts],
        ["Questions answered", totals.questionsAnswered],
        ["Average score", `${totals.averageScore.toFixed(1)} / 10`],
        ["Best score", `${totals.bestScore} / 10`],
      ]) {
        const card = element("div", label, "metric");
        card.append(element("strong", String(value)));
        metrics.append(card);
      }
      body.append(metrics);
      const table = element("table");
      table.append(element("caption", "Scores by topic and difficulty"));
      const head = element("thead"),
        heading = element("tr");
      for (const title of [
        "Track / topic",
        "Level",
        "Questions",
        "Attempts",
        "Average /10",
        "Best /10",
        "Last practised",
      ]) {
        const th = element("th", title);
        th.scope = "col";
        heading.append(th);
      }
      head.append(heading);
      const tbody = element("tbody");
      for (const row of rows) {
        const tr = element("tr");
        for (const value of [
          `${row.track} / ${row.topic}`,
          row.level,
          row.questionsAnswered,
          row.attempts,
          row.averageScore.toFixed(1),
          row.bestScore,
          date(row.lastPracticedAt),
        ])
          tr.append(element("td", String(value)));
        tbody.append(tr);
      }
      table.append(head, tbody);
      const scroll = element("div", undefined, "scroll");
      scroll.append(table);
      body.append(scroll);
      body.append(
        element(
          "p",
          "Average scores include every saved attempt, including retries. They are not a mastery percentage.",
          "hint",
        ),
      );
    });
  }

  #sessions(page) {
    return this.#load("sessions", async (signal) => {
      const result = await this.#client.sessions(page, signal);
      if (signal.aborted) return;
      const body = this.#el("sessions-body");
      body.replaceChildren();
      if (!result.content.length)
        body.append(
          element(
            "p",
            "No sessions on this page. Start an interview to build your history.",
          ),
        );
      for (const session of result.content) {
        const card = element("article");
        card.append(
          element(
            "h3",
            `${session.track} → ${session.topic} → ${session.level}`,
          ),
        );
        card.append(
          element(
            "p",
            `${session.status === "ACTIVE" ? "In progress" : "Completed"} · Started ${date(session.createdAt)}`,
          ),
        );
        if (session.resume)
          card.append(
            element(
              "p",
              `Résumé: ${session.resume.filename} · Confirmed skills: ${session.resume.skills.map((s) => s.name).join(", ")}`,
              "hint",
            ),
          );
        if (session.completedAt)
          card.append(element("p", `Finished ${date(session.completedAt)}`));
        const actions = element("div", undefined, "actions");
        const inspect = element(
          "button",
          "View questions & feedback",
          "secondary",
        );
        inspect.addEventListener("click", () => this.#openSession(session));
        actions.append(inspect, this.#sessionLink(session));
        card.append(actions);
        body.append(card);
      }
      this.#pager("sessions", result, (next) => this.#sessions(next));
    });
  }

  #sessionLink(session) {
    const link = element(
      "a",
      session.status === "ACTIVE" ? "Resume interview" : "Open saved interview",
      "button",
    );
    link.href = `/practice.html?session=${encodeURIComponent(session.id)}`;
    return link;
  }

  #openSession(session) {
    this.#session = session;
    this.#question = undefined;
    this.#controllers.get("answers")?.abort();
    this.#controllers.delete("answers");
    this.#el("answers").hidden = true;
    this.#el("questions").hidden = false;
    this.#el("questions-body").replaceChildren();
    this.#el("questions-pager").replaceChildren();
    this.#el("session-description").textContent =
      `${session.track} → ${session.topic} → ${session.level} · ${session.status === "ACTIVE" ? "In progress" : "Completed"}`;
    this.#el("session-actions").replaceChildren(this.#sessionLink(session));
    this.#el("questions-title").focus();
    this.#questions(0);
  }

  #questions(page) {
    const session = this.#session;
    return this.#load("questions", async (signal) => {
      const result = await this.#client.questions(session.id, page, signal);
      if (signal.aborted) return;
      const body = this.#el("questions-body");
      body.replaceChildren();
      if (!result.content.length)
        body.append(
          element(
            "p",
            session.status === "ACTIVE"
              ? "No saved questions on this page. Resume this interview to get a question."
              : "No saved questions on this page of the completed session.",
          ),
        );
      for (const question of result.content) {
        const card = element("article");
        card.append(
          element("p", question.question),
          element("p", `Asked ${date(question.createdAt)}`, "hint"),
        );
        if (question.skill)
          card.append(element("p", `Skill: ${question.skill}`, "hint"));
        if (question.focus)
          card.append(element("p", question.focus.reason, "hint"));
        const button = element("button", "View answer attempts", "secondary");
        button.addEventListener("click", () => {
          this.#question = question;
          this.#el("answers").hidden = false;
          this.#el("selected-question").textContent = question.question;
          this.#el("answers-body").replaceChildren();
          this.#el("answers-pager").replaceChildren();
          this.#el("answers-title").focus();
          this.#answers(0);
        });
        card.append(button);
        body.append(card);
      }
      this.#pager("questions", result, (next) => this.#questions(next));
    });
  }

  #answers(page) {
    const question = this.#question;
    return this.#load("answers", async (signal) => {
      const result = await this.#client.answers(question.id, page, signal);
      if (signal.aborted) return;
      const body = this.#el("answers-body");
      body.replaceChildren();
      if (!result.content.length)
        body.append(
          element(
            "p",
            "No saved answer attempts for this question on this page.",
          ),
        );
      for (const attempt of result.content) {
        const card = element("article"),
          feedback = attempt.feedback;
        card.append(
          element("h3", `Score ${feedback.score} / ${feedback.maxScore}`),
          element(
            "p",
            `Submitted ${date(attempt.submittedAt)} · Rubric ${attempt.rubricVersion}`,
            "hint",
          ),
          element("h4", "Your answer"),
          element("p", attempt.answer),
          element("h4", "Explanation"),
          element("p", feedback.explanation),
        );
        for (const [label, points] of [
          ["Correct points", feedback.correctPoints],
          ["Missing points", feedback.missingPoints],
        ]) {
          card.append(element("h4", label));
          const ul = element("ul");
          for (const point of points.length ? points : ["None identified."])
            ul.append(element("li", point));
          card.append(ul);
        }
        const details = element("details");
        details.append(element("summary", "Rubric breakdown"));
        for (const [key, label, max] of [
          ["correctness", "Correctness", 5],
          ["completeness", "Completeness", 3],
          ["clarity", "Clarity", 2],
        ]) {
          details.append(
            element(
              "p",
              `${label}: ${feedback.rubric[key].score} / ${max} — ${feedback.rubric[key].reason}`,
            ),
          );
        }
        card.append(details);
        body.append(card);
      }
      this.#pager("answers", result, (next) => this.#answers(next));
    });
  }

  #pager(key, result, go) {
    const root = this.#el(`${key}-pager`);
    root.replaceChildren();
    const previous = element("button", "Previous", "secondary"),
      next = element("button", "Next", "secondary");
    previous.disabled = result.page === 0;
    next.disabled = result.page + 1 >= result.totalPages;
    previous.addEventListener("click", () => go(result.page - 1));
    next.addEventListener("click", () => go(result.page + 1));
    root.append(
      previous,
      element(
        "span",
        `${result.totalElements} records · Page ${result.totalPages ? result.page + 1 : 0} of ${result.totalPages}`,
      ),
      next,
    );
  }
}
if (!customElements.get("interview-history"))
  customElements.define("interview-history", InterviewHistory);
