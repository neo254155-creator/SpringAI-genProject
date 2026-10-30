import { ResumeClient } from "./resume-client.js";
import { InterviewFlow } from "./interview-client.js";
import "./coach.js";

export class ResumeInterviewSetup extends HTMLElement {
  #client = new ResumeClient();
  #flow = new InterviewFlow();
  #profile;
  #controller;
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this.shadowRoot.innerHTML = `
 <link rel="stylesheet" href="/ui/resume.css">
 <div class="workspace" role="region" aria-label="Resume interview setup">
  <span class="eyebrow">YOUR RESUME. YOUR INTERVIEW.</span><h1>Let your experience lead.</h1>
  <p>Upload your résumé, review the technical skills found in it, and practise questions based on the skills you confirm.</p>
  <div class="flow-steps"><span class="flow-step active" id="upload-step"><i>01</i> YOUR RESUME</span><span class="flow-step" id="review-step"><i>02</i> CONFIRM SKILLS</span><span class="flow-step"><i>03</i> INTERVIEW</span></div>
  <form id="extract-form"><div class="source-choice"><label><input type="radio" name="source" value="file" checked> Upload a file</label><label><input type="radio" name="source" value="text"> Paste résumé text</label></div>
   <div id="file-source"><label class="upload-zone" for="resume-file"><span class="upload-symbol">↑</span><strong>Choose your résumé</strong><span>DOCX, TXT, or Markdown · up to 1 MiB</span><input id="resume-file" type="file" accept=".docx,.txt,.md" required></label><p class="hint">PDF and scanned images are not supported yet. You can copy text from your PDF into the Paste résumé text option.</p></div>
   <div id="text-source" hidden><label for="resume-text">Résumé text</label><textarea id="resume-text" maxlength="24000" rows="10" placeholder="Paste your résumé, including technical skills and project experience…" disabled></textarea><p class="hint">Up to 24,000 characters. No preset Java or Spring Boot skill list.</p></div>
   <p class="hint">Your résumé text is sent to the configured AI provider for skill extraction. The app saves the filename and extracted skill excerpts, not the original file or full résumé text. This workspace is shared.</p>
   <button id="extract" type="submit">Extract my technical skills →</button>
  </form>
  <p id="status" role="status"></p><p id="error" role="alert" hidden></p>
  <section id="review" hidden aria-labelledby="review-title"><span class="eyebrow">REVIEW BEFORE YOU BEGIN</span><h2 id="review-title" tabindex="-1">Which skills should we explore?</h2><p id="profile-name"></p><p class="hint">Each skill below has a supporting excerpt from your résumé. Uncheck anything inaccurate or anything you don’t want to practise. Extraction does not establish proficiency.</p><div id="skills"></div>
   <form id="start-form"><label for="level">Choose interview difficulty</label><select id="level" required><option value="" disabled selected>Choose your level</option><option value="beginner">Beginner</option><option value="intermediate">Intermediate</option><option value="advanced">Advanced</option></select><p class="hint">You choose the difficulty; it is not guessed from your résumé.</p><div class="actions"><button id="start-resume" type="submit">Start my résumé interview →</button><button id="replace" type="button" class="secondary">Use a different résumé</button></div></form>
  </section><button id="retry-profile" type="button" class="secondary" hidden>Retry opening résumé</button><button id="stop" type="button" class="secondary" hidden>Stop waiting</button>
 </div>`;
    this.shadowRoot
      .querySelectorAll("[name=source]")
      .forEach((r) => r.addEventListener("change", () => this.#source()));
    this.#el("extract-form").addEventListener("submit", (e) => {
      e.preventDefault();
      this.#run(
        "Reading your résumé and extracting supported technical skills…",
        async (signal) => {
          this.#profile =
            this.shadowRoot.querySelector("[name=source]:checked").value ===
            "file"
              ? await this.#client.upload(
                  this.#el("resume-file").files[0],
                  signal,
                )
              : await this.#client.paste(this.#el("resume-text").value, signal);
          const url = new URL(location.href);
          url.search = "";
          url.searchParams.set("resume", this.#profile.id);
          history.replaceState(null, "", url);
          this.#showProfile();
        },
        "Review the extracted skills before starting.",
      );
    });
    this.#el("start-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const skills = [
        ...this.#el("skills").querySelectorAll("input:checked"),
      ].map((c) => c.value);
      if (!skills.length) {
        this.#showError("Select at least one extracted skill.");
        return;
      }
      this.#run(
        "Saving your confirmed skills and preparing the first question…",
        async (signal) => {
          try {
            await this.#flow.startFromResume(
              this.#profile.id,
              { skills, level: this.#el("level").value },
              signal,
              (session) => {
                const url = new URL(location.href);
                url.search = "";
                url.searchParams.set("session", session.id);
                history.replaceState(null, "", url);
              },
            );
          } finally {
            if (this.#flow.session) this.#openSession(this.#flow.session.id);
          }
        },
        "Interview ready.",
      );
    });
    this.#el("replace").addEventListener("click", () => {
      this.#profile = undefined;
      const url = new URL(location.href);
      url.search = "";
      history.replaceState(null, "", url);
      this.#el("review").hidden = true;
      this.#el("extract-form").hidden = false;
      this.#el("upload-step").classList.add("active");
      this.#el("review-step").classList.remove("active");
      this.#el("status").textContent =
        "Choose another résumé or update its text.";
    });
    this.#el("stop").addEventListener("click", () => this.#controller?.abort());
    this.#el("retry-profile").addEventListener("click", () =>
      this.#loadProfile(),
    );
  }
  #el(id) {
    return this.shadowRoot.getElementById(id);
  }
  connectedCallback() {
    const url = new URL(location.href);
    if (url.searchParams.has("session")) {
      this.#mountCoach();
      return;
    }
    if (url.searchParams.has("resume")) this.#loadProfile();
  }
  disconnectedCallback() {
    this.#controller?.abort();
  }
  #mountCoach() {
    const style = document.createElement("link");
    style.rel = "stylesheet";
    style.href = "/ui/resume.css";
    this.shadowRoot.replaceChildren(
      style,
      document.createElement("java-interview-coach"),
    );
  }
  #openSession(id) {
    const url = new URL(location.href);
    url.search = "";
    url.searchParams.set("session", id);
    history.replaceState(null, "", url);
    this.#mountCoach();
  }
  #source() {
    const file =
      this.shadowRoot.querySelector("[name=source]:checked").value === "file";
    this.#el("file-source").hidden = !file;
    this.#el("text-source").hidden = file;
    this.#el("resume-file").disabled = !file;
    this.#el("resume-file").required = file;
    this.#el("resume-text").disabled = file;
    this.#el("resume-text").required = !file;
  }
  #loadProfile() {
    this.#el("retry-profile").hidden = true;
    this.#run(
      "Opening your saved résumé skills…",
      async (signal) => {
        this.#profile = await this.#client.get(
          new URL(location.href).searchParams.get("resume"),
          signal,
        );
        this.#showProfile();
      },
      "Review the skills for this interview.",
    );
  }
  #showProfile() {
    this.#el("extract-form").hidden = true;
    this.#el("review").hidden = false;
    this.#el("profile-name").textContent = this.#profile.filename;
    this.#el("upload-step").classList.remove("active");
    this.#el("review-step").classList.add("active");
    const skills = this.#el("skills");
    skills.replaceChildren();
    const requested = new URL(location.href).searchParams.get("skill");
    for (const skill of this.#profile.skills) {
      const label = document.createElement("label");
      label.className = "resume-skill";
      const check = document.createElement("input");
      check.type = "checkbox";
      check.value = skill.name;
      check.checked = !requested || requested === skill.name;
      const content = document.createElement("span");
      const title = document.createElement("strong");
      title.textContent = skill.name;
      const quote = document.createElement("q");
      quote.textContent = skill.evidence;
      content.append(title, quote);
      label.append(check, content);
      skills.append(label);
    }
    if (!this.#profile.skills.length) {
      const p = document.createElement("p");
      p.textContent =
        "No supported technical skills were extracted. Check the résumé text, explicitly include your technologies, or use another résumé. No default skills have been added.";
      skills.append(p);
    }
    this.#el("start-resume").disabled = !this.#profile.skills.length;
    const level = new URL(location.href).searchParams.get("level");
    if (["beginner", "intermediate", "advanced"].includes(level))
      this.#el("level").value = level;
    this.#el("review-title").focus();
  }
  #showError(message) {
    let error = this.#el("error");
    if (!error) {
      error = document.createElement("p");
      error.id = "error";
      error.setAttribute("role", "alert");
      this.shadowRoot.prepend(error);
    }
    error.textContent = message;
    error.hidden = false;
  }
  async #run(message, action, success) {
    if (this.#controller) return;
    const controller = new AbortController();
    this.#controller = controller;
    const timer = setTimeout(() => controller.abort(), 120000);
    const workspace = this.shadowRoot.querySelector(".workspace");
    workspace?.setAttribute("aria-busy", "true");
    this.#el("error").hidden = true;
    this.#el("status").textContent = message;
    this.#el("stop").hidden = false;
    this.shadowRoot
      .querySelectorAll("button:not(#stop),input,textarea,select")
      .forEach((n) => (n.disabled = true));
    try {
      await action(controller.signal);
      if (this.#el("status")) this.#el("status").textContent = success;
    } catch (error) {
      this.#showError(
        controller.signal.aborted
          ? "Stopped waiting. The server may still complete this request. You can retry or reopen the saved session."
          : error instanceof TypeError
            ? "Could not reach the server. Your input has been kept."
            : error.message,
      );
    } finally {
      clearTimeout(timer);
      this.#controller = undefined;
      workspace?.setAttribute("aria-busy", "false");
      if (this.#el("stop")) {
        this.#el("stop").hidden = true;
        this.shadowRoot
          .querySelectorAll("button,input,textarea,select")
          .forEach((n) => (n.disabled = false));
        this.#source();
        this.#el("start-resume").disabled = !this.#profile?.skills.length;
        this.#el("retry-profile").hidden =
          !!this.#profile || !new URL(location.href).searchParams.has("resume");
      }
    }
  }
}
if (!customElements.get("resume-interview-setup"))
  customElements.define("resume-interview-setup", ResumeInterviewSetup);
