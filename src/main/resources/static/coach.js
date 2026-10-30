import { InterviewFlow, requestJson } from "./interview-client.js";

export class JavaInterviewCoach extends HTMLElement {
  #flow = new InterviewFlow();
  #controller;
  #ready = false;
  #shownQuestion;
  #resumeId;
  #timer;
  #visitStarted = Date.now();

  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this.shadowRoot.innerHTML = `
      <link rel="stylesheet" href="/ui/workspace.css">
      <div class="workspace" role="region" aria-label="Interview practice">
        <div class="workspace-top"><div><span class="eyebrow">YOUR SPACE TO THINK</span>
        <h1>One question. Your full potential.</h1>
        <p>Take your time. Think it through. Make this answer yours.</p></div>
        <div class="visit-clock" aria-label="Time on this screen">THIS VISIT <span id="visit-time">00:00</span></div></div>
        <div class="flow-steps" aria-label="Interview stages"><span class="flow-step active" id="stage-select"><i>01</i> CONFIRMED SKILLS</span><span class="flow-step" id="stage-answer"><i>02</i> YOUR ANSWER</span><span class="flow-step" id="stage-feedback"><i>03</i> FEEDBACK</span></div>
        <form id="selection-form">
          <div class="fields">
            <label>Track<select name="track" required disabled></select></label>
            <label>Topic<select name="topic" required disabled></select></label>
            <label>Level<select name="level" required disabled></select></label>
          </div>
          <button id="start" type="submit" disabled>Start interview</button>
        </form>
        <p id="status" role="status">Loading interview options…</p>
        <p id="error" role="alert" hidden></p>
        <p id="session-info" hidden></p>
        <section id="question-section" aria-labelledby="question-heading" hidden>
          <div class="question-top"><h2 id="question-heading" tabindex="-1">YOUR QUESTION</h2><span id="question-number"></span></div>
          <p id="question"></p>
          <p id="question-focus" hidden></p>
          <form id="answer-form">
            <label for="answer">Your answer</label>
            <textarea id="answer" name="answer" maxlength="8000" required aria-describedby="answer-count" placeholder="Start with the main idea. Explain your reasoning, then add an example…"></textarea>
            <p id="answer-count">0 / 8,000 characters</p>
            <button id="submit-answer" type="submit">Submit answer</button>
          </form>
        </section>
        <section id="feedback-section" aria-labelledby="feedback-heading" hidden>
          <h2 id="feedback-heading" tabindex="-1">Your feedback</h2>
          <p id="score"></p>
          <h3>Your strengths</h3><ul id="correct-points"></ul>
          <h3>Areas for improvement</h3><ul id="missing-points"></ul>
          <h3>Explanation</h3><p id="explanation"></p>
          <div class="rubric">
            <article><h3>Correctness <span id="correctness-score"></span></h3><p id="correctness-reason"></p></article>
            <article><h3>Completeness <span id="completeness-score"></span></h3><p id="completeness-reason"></p></article>
            <article><h3>Clarity <span id="clarity-score"></span></h3><p id="clarity-reason"></p></article>
          </div>
          <details class="planned-details"><summary>About this evaluation · what’s available</summary><p>Your score uses correctness, completeness, and clarity. Separate relevance/structure scores, improved example answers, and a list of suggested follow-up questions are planned. Next question uses your saved feedback to generate a targeted question.</p></details>
        </section>
        <div class="actions">
          <button id="next" type="button" hidden>Next question</button>
          <button id="finish" class="secondary" type="button" hidden>End interview</button>
          <button id="new" type="button" hidden>Start another interview</button>
          <button id="reload" type="button" hidden>Reload options</button>
          <button id="cancel" class="secondary" type="button" hidden>Stop waiting</button>
        </div>
        <p class="next-note">Open-ended practice · no fixed question limit. The timer measures this page visit only.</p>
        <dialog id="end-dialog" aria-labelledby="end-title"><h2 id="end-title">Finish for now?</h2><p>Your submitted answers and feedback will stay in history. Any unsubmitted answer will not be saved. This session will close to new questions.</p><div class="actions"><button id="keep-practicing" class="secondary" autofocus>Keep practicing</button><button id="confirm-end">End interview</button></div></dialog>
      </div>`;
    this.#el("selection-form").addEventListener("submit", (event) => {
      event.preventDefault();
      const selection = Object.fromEntries(
        new FormData(this.#el("selection-form")),
      );
      this.#run(
        "Preparing your question…",
        (signal) =>
          this.#flow.session
            ? this.#flow.nextQuestion(signal)
            : this.#flow.start(selection, signal),
        "Your question is ready.",
        "question-heading",
      );
    });
    this.#el("answer-form").addEventListener("submit", (event) => {
      event.preventDefault();
      const answer = this.#el("answer").value;
      this.#run(
        "Reviewing your answer…",
        (signal) => this.#flow.submit(answer, signal),
        "Your feedback is ready.",
        "feedback-heading",
      );
    });
    this.#el("answer").addEventListener("input", () => this.#updateCount());
    this.#el("next").addEventListener("click", () =>
      this.#run(
        "Preparing your next question…",
        (signal) => this.#flow.nextQuestion(signal),
        "Your next question is ready.",
        "question-heading",
      ),
    );
    this.#el("finish").addEventListener("click", () =>
      this.#el("end-dialog").showModal(),
    );
    this.#el("keep-practicing").addEventListener("click", () =>
      this.#el("end-dialog").close(),
    );
    this.#el("confirm-end").addEventListener("click", () => {
      this.#el("end-dialog").close();
      this.#run(
        "Finishing your interview…",
        (signal) => this.#flow.complete(signal),
        "Interview completed. Your submitted answers and feedback have been saved.",
      );
    });
    this.#el("new").addEventListener("click", () => {
      this.#flow.reset();
      this.#resumeId = undefined;
      const url = new URL(window.location.href);
      url.searchParams.delete("session");
      window.history.replaceState(null, "", url);
      this.#ready = false;
      this.#el("error").hidden = true;
      this.#el("status").textContent = "Choose your focus to begin again.";
      this.#render();
      location.assign("/practice.html");
    });
    this.#el("reload").addEventListener("click", () =>
      this.#resumeId ? this.#loadSession() : this.#loadOptions(),
    );
    this.#el("cancel").addEventListener("click", () =>
      this.#controller?.abort(),
    );
  }

  connectedCallback() {
    this.dataset.motion = document.documentElement.dataset.motion || "on";
    clearInterval(this.#timer);
    this.#timer = setInterval(() => {
      const seconds = Math.floor((Date.now() - this.#visitStarted) / 1000);
      this.#el("visit-time").textContent =
        `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
    }, 1000);
    if (this.#ready || this.#controller) return;
    this.#resumeId = new URL(window.location.href).searchParams.get("session");
    if (this.#resumeId) this.#loadSession();
    else location.replace("/practice.html");
  }
  disconnectedCallback() {
    this.#controller?.abort();
    clearInterval(this.#timer);
  }
  #el(id) {
    return this.shadowRoot.getElementById(id);
  }
  #updateCount() {
    this.#el("answer-count").textContent =
      `${this.#el("answer").value.length.toLocaleString()} / 8,000 characters`;
  }

  #loadSession() {
    this.#run(
      "Opening saved interview…",
      async (signal) => {
        await this.#flow.resume(this.#resumeId, signal);
        for (const name of ["track", "topic", "level"]) {
          const value = this.#flow.session[name];
          this.shadowRoot
            .querySelector(`[name=${name}]`)
            .replaceChildren(new Option(value, value));
        }
        this.#ready = true;
      },
      "Saved interview opened. Continue from the latest saved question.",
    );
  }

  #loadOptions() {
    this.#run(
      "Loading interview options…",
      async (signal) => {
        const options = await requestJson("/interview/options", { signal });
        for (const [name, key, preferred] of [
          ["track", "tracks", "Java"],
          ["topic", "topics", "Spring Boot"],
          ["level", "levels", "intermediate"],
        ]) {
          if (
            !Array.isArray(options?.[key]) ||
            !options[key].length ||
            !options[key].every((v) => typeof v === "string")
          ) {
            throw new Error("The server returned invalid interview options.");
          }
          const select = this.shadowRoot.querySelector(`[name=${name}]`);
          select.replaceChildren(
            ...options[key].map((value) => new Option(value, value)),
          );
          const requested = new URL(location.href).searchParams.get(name);
          if (options[key].includes(requested)) select.value = requested;
          else if (options[key].includes(preferred)) select.value = preferred;
        }
        this.#ready = true;
      },
      "Choose your focus to begin.",
    );
  }

  async #run(message, action, success, focusId) {
    if (this.#controller) return;
    const controller = new AbortController();
    this.#controller = controller;
    const timeout = setTimeout(
      () => controller.abort(new DOMException("Timed out", "TimeoutError")),
      120000,
    );
    this.#el("error").hidden = true;
    this.#el("status").textContent = message;
    this.#render();
    let succeeded = false;
    try {
      await action(controller.signal);
      this.#el("status").textContent = success;
      succeeded = true;
    } catch (cause) {
      this.#el("status").textContent = "Request not completed in this screen.";
      this.#el("error").textContent = controller.signal.aborted
        ? "Stopped waiting. The server may still finish and save this request. Your typed answer has been kept."
        : cause instanceof TypeError
          ? "Could not reach the server. Your typed answer has been kept."
          : cause.message;
      this.#el("error").hidden = false;
    } finally {
      clearTimeout(timeout);
      this.#controller = undefined;
      if (this.#flow.session) {
        const url = new URL(window.location.href);
        url.searchParams.set("session", this.#flow.session.id);
        window.history.replaceState(null, "", url);
      }
      this.#render();
      if (succeeded && focusId) this.#el(focusId).focus();
    }
  }

  #render() {
    const { session, question, feedback } = this.#flow;
    const busy = Boolean(this.#controller);
    const active = session?.status === "ACTIVE";
    const completed = session?.status === "COMPLETED";
    this.shadowRoot
      .querySelector(".workspace")
      .setAttribute("aria-busy", String(busy));
    this.shadowRoot.querySelectorAll("select").forEach((select) => {
      select.disabled = busy || !this.#ready || Boolean(session);
    });
    this.#el("start").hidden = Boolean(question) || completed;
    this.#el("start").disabled = busy || !this.#ready;
    this.#el("start").textContent = session?.resume
      ? "Ask my first question"
      : session
        ? "Retry first question"
        : "Start interview";
    this.shadowRoot.querySelector(".fields").hidden = Boolean(session?.resume);
    this.#el("reload").hidden = this.#ready || busy;
    this.#el("reload").textContent = this.#resumeId
      ? "Retry opening session"
      : "Reload options";
    this.#el("cancel").hidden = !busy;
    this.#el("session-info").hidden = !session;
    if (session)
      this.#el("session-info").textContent =
        `${session.resume ? "Confirmed skills: " + session.resume.skills.map((s) => s.name).join(", ") : session.track + " → " + session.topic} · ${session.level} · ${completed ? "Completed" : "In progress"} · ${this.#flow.answered} answered`;
    for (const [id, selected] of [
      ["stage-select", !question],
      ["stage-answer", Boolean(question) && !feedback],
      ["stage-feedback", Boolean(feedback)],
    ]) {
      this.#el(id).classList.toggle("active", selected);
      if (selected) this.#el(id).setAttribute("aria-current", "step");
      else this.#el(id).removeAttribute("aria-current");
    }
    this.#el("question-number").textContent =
      `${question?.skill ? question.skill + " · " : ""}${this.#flow.answered} answered · ${feedback ? "Reviewed" : "In progress"}`;
    this.#el("question-section").hidden = !question;
    if (question) {
      this.#el("question").textContent = question.question;
      this.#el("question-focus").hidden = !question.focus;
      if (question.focus)
        this.#el("question-focus").textContent = question.focus.reason;
      if (question.id !== this.#shownQuestion) {
        this.#el("answer").value = this.#flow.savedAnswer;
        this.#shownQuestion = question.id;
        this.#updateCount();
      }
    }
    this.#el("answer").disabled = busy || !active || Boolean(feedback);
    this.#el("submit-answer").hidden = !active || Boolean(feedback);
    this.#el("submit-answer").disabled = busy;
    this.#el("feedback-section").hidden = !feedback;
    if (feedback) {
      this.#el("score").textContent =
        `${feedback.score} / ${feedback.maxScore}`;
      this.#list(
        "correct-points",
        feedback.correctPoints,
        "No correct points were identified.",
      );
      this.#list(
        "missing-points",
        feedback.missingPoints,
        "No missing points were identified.",
      );
      this.#el("explanation").textContent = feedback.explanation;
      for (const [key, maximum] of [
        ["correctness", 5],
        ["completeness", 3],
        ["clarity", 2],
      ]) {
        this.#el(`${key}-score`).textContent =
          `${feedback.rubric[key].score} / ${maximum}`;
        this.#el(`${key}-reason`).textContent = feedback.rubric[key].reason;
      }
    }
    this.#el("next").hidden = !active || !feedback;
    this.#el("finish").hidden = !active;
    this.#el("new").hidden = !completed;
    for (const id of ["next", "finish", "new"]) this.#el(id).disabled = busy;
  }

  #list(id, points, empty) {
    // All model-provided strings are rendered as text, never HTML.
    this.#el(id).replaceChildren(
      ...(points.length ? points : [empty]).map((text) => {
        const li = document.createElement("li");
        li.textContent = text;
        return li;
      }),
    );
  }
}

if (!customElements.get("java-interview-coach"))
  customElements.define("java-interview-coach", JavaInterviewCoach);
