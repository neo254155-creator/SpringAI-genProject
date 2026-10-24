/** First milestone: select an interview focus and request one question. */
export async function requestQuestion(selection, { signal } = {}) {
  const response = await fetch('/interview/questions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(selection),
    signal,
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.detail || result.error || 'Could not generate a question. Please try again.');
  if (typeof result.question !== 'string' || !result.question.trim()) {
    throw new Error('No question was returned. Please try again.');
  }
  return result;
}

export class JavaInterviewCoach extends HTMLElement {
  #controller;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    this.shadowRoot.innerHTML = `
      <style>
        :host { display:block; color:#17243a; font:16px/1.6 system-ui,sans-serif; }
        * { box-sizing:border-box; }
        main { max-width:850px; margin:auto; padding:32px; background:white; border-radius:20px; }
        .eyebrow { color:#405fc2; font-size:12px; font-weight:700; letter-spacing:.12em; }
        h1 { line-height:1.2; } p { color:#526077; }
        .fields { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; }
        label { font-weight:600; } select { display:block; width:100%; padding:12px; margin:8px 0 24px; font:inherit; }
        button { padding:12px 20px; background:#3153b9; color:white; border:0; border-radius:8px; font:inherit; cursor:pointer; }
        button:disabled { opacity:.6; cursor:wait; }
        :focus-visible { outline:3px solid #496cda; outline-offset:3px; }
        #question { white-space:pre-wrap; overflow-wrap:anywhere; color:#17243a; font-size:20px; }
        #error { color:#a32132; } [hidden] { display:none !important; }
        section { margin-top:28px; border-top:1px solid #dce3ed; }
        @media(max-width:600px) { .fields { grid-template-columns:1fr; gap:0; } main { padding:24px; } }
      </style>
      <main>
        <span class="eyebrow">JAVA / INTERVIEW PRACTICE</span>
        <h1>Start with one question.</h1>
        <p>Choose your focus and difficulty, then practise answering aloud.</p>
        <form>
          <div class="fields">
            <label>Track<select name="track" required disabled></select></label>
            <label>Topic<select name="topic" required disabled></select></label>
            <label>Level<select name="level" required disabled></select></label>
          </div>
          <button type="submit" disabled>Get interview question</button>
          <button type="button" id="retry" hidden>Reload options</button>
          <button type="button" id="cancel" hidden>Cancel</button>
        </form>
        <p id="status" role="status">Loading interview options…</p>
        <p id="error" role="alert" hidden></p>
        <section aria-label="Interview question" aria-busy="false" hidden>
          <h2>Your question</h2>
          <p id="selection"></p>
          <p id="question"></p>
          <p>Take your time and practise your answer aloud. This screen currently shows questions only.</p>
        </section>
      </main>`;
    this.shadowRoot.querySelector('form').addEventListener('submit', event => {
      event.preventDefault();
      this.#run(false);
    });
    this.shadowRoot.querySelector('#retry').addEventListener('click', () => this.#run(true));
    this.shadowRoot.querySelector('#cancel').addEventListener('click', () => this.#controller?.abort());
  }

  connectedCallback() { this.#run(true); }
  disconnectedCallback() { this.#controller?.abort(); }

  async #run(loadOptions) {
    if (this.#controller) return;
    const root = this.shadowRoot;
    const controller = new AbortController();
    this.#controller = controller;
    const timeout = setTimeout(() => controller.abort(new DOMException('Timed out', 'TimeoutError')), 120000);
    const status = root.querySelector('#status');
    const error = root.querySelector('#error');
    const section = root.querySelector('section');
    error.hidden = true;
    root.querySelector('#retry').hidden = true;
    root.querySelector('#cancel').hidden = false;
    root.querySelector('[type=submit]').disabled = true;
    const selection = Object.fromEntries(new FormData(root.querySelector('form')));
    root.querySelectorAll('select').forEach(select => { select.disabled = true; });
    status.textContent = loadOptions ? 'Loading interview options…' : 'Preparing your question…';
    if (!loadOptions) section.hidden = true;
    section.setAttribute('aria-busy', 'true');
    let ready = !loadOptions;
    try {
      if (loadOptions) {
        const response = await fetch('/interview/options', { signal: controller.signal });
        if (!response.ok) throw new Error('Could not load interview options.');
        const options = await response.json();
        for (const [name, key, preferred] of [['track', 'tracks', 'Java'], ['topic', 'topics', 'Spring Boot'], ['level', 'levels', 'intermediate']]) {
          if (!Array.isArray(options[key]) || !options[key].length) throw new Error('Invalid interview options.');
          const select = root.querySelector(`[name=${name}]`);
          select.replaceChildren(...options[key].map(value => new Option(value, value)));
          if (options[key].includes(preferred)) select.value = preferred;
        }
        ready = true;
        status.textContent = 'Choose your focus to begin.';
      } else {
        const result = await requestQuestion(selection, { signal: controller.signal });
        root.querySelector('#selection').textContent = `${result.track} → ${result.topic} → ${result.level}`;
        // Model output is untrusted; never insert it as HTML.
        root.querySelector('#question').textContent = result.question;
        section.hidden = false;
        status.textContent = 'Your question is ready.';
      }
    } catch (cause) {
      status.textContent = controller.signal.aborted ? 'Request stopped.' : 'Request failed.';
      error.textContent = controller.signal.reason?.name === 'TimeoutError'
        ? 'The request took too long. Please try again.'
        : controller.signal.aborted ? 'Request cancelled.'
        : cause instanceof TypeError ? 'Could not reach the server. Please try again.' : cause.message;
      error.hidden = false;
    } finally {
      clearTimeout(timeout);
      this.#controller = undefined;
      root.querySelector('#cancel').hidden = true;
      root.querySelector('#retry').hidden = ready;
      root.querySelector('[type=submit]').disabled = !ready;
      root.querySelectorAll('select').forEach(select => { select.disabled = !ready; });
      section.setAttribute('aria-busy', 'false');
    }
  }
}
if (!customElements.get('java-interview-coach')) customElements.define('java-interview-coach', JavaInterviewCoach);
