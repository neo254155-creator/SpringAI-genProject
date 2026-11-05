import { DocumentsClient, DocumentFlow } from "./documents-client.js";
import { el, date, icon } from "./components.js";

export async function renderDocuments(root) {
  const client = new DocumentsClient(), flow = new DocumentFlow(client);
  let selected = null, page = 0, totalPages = 0, controller = null;
  root.innerHTML = `
    <div class="page-heading"><div><span class="eyebrow">A LITTLE CONTEXT GOES A LONG WAY</span>
      <h1>Learn from your notes.</h1><p>Optional study support for your résumé interview practice.</p></div>
      <a class="btn secondary" href="/practice.html">${icon("chat")} Résumé interview</a></div>
    <div class="notes-layout">
      <aside class="notes-library" aria-label="Your study notes">
        <section class="panel"><h2>Add study notes</h2>
          <form id="notes-upload" class="notes-form">
            <label for="notes-title">Document title</label><input id="notes-title" name="title" maxlength="200" required placeholder="e.g. Spring Boot revision" />
            <label for="notes-file">Text or Markdown file</label><input id="notes-file" name="file" type="file" accept=".txt,.md" required />
            <p class="tiny muted">UTF-8 TXT or MD · up to 64 KiB. Upload saves text without calling AI. Saved notes belong to your account.</p>
            <button class="btn" type="submit">${icon("book")} Save notes</button>
          </form></section>
        <section class="panel"><div class="panel-head"><h2>Saved notes</h2>
          <button id="notes-refresh" class="icon-btn" aria-label="Refresh saved notes">${icon("refresh")}</button></div>
          <div id="notes-list"></div><div class="notes-pagination">
            <button id="notes-previous" class="btn secondary" type="button">Previous</button>
            <span id="notes-page" class="tiny muted"></span><button id="notes-next" class="btn secondary" type="button">Next</button>
          </div></section>
      </aside>
      <section class="panel notes-reader" aria-labelledby="selected-title">
        <span class="eyebrow">FROM YOUR SOURCES</span><h2 id="selected-title">Choose a document</h2>
        <p id="selected-meta" class="muted">Save notes or choose an existing document to begin.</p>
        <div id="notes-content" hidden>
          <div class="notes-index"><div><strong>Prepare your notes for questions</strong>
            <p id="notes-index-state" class="tiny muted">Index once before asking. If indexed earlier, you can ask directly.</p></div>
            <button id="notes-index" class="btn secondary" type="button">Index notes</button></div>
          <form id="notes-question" class="notes-form">
            <label for="notes-query">What would you like to understand?</label>
            <textarea id="notes-query" maxlength="2000" rows="4" required placeholder="Ask a question your notes can answer…"></textarea>
            <p class="tiny muted">Indexing uses Gemini. Each new question uses Gemini and Groq. Nothing runs automatically; repeated questions reuse this visit’s saved answer.</p>
            <button class="btn" type="submit">${icon("spark")} Ask my notes</button>
          </form></div>
        <p id="notes-status" role="status" aria-live="polite"></p>
        <p id="notes-error" class="notes-error" role="alert" hidden></p>
        <button id="notes-stop" class="btn secondary" type="button" hidden>Stop waiting</button>
        <section id="notes-answer" class="notes-answer" aria-labelledby="notes-answer-title" hidden>
          <h2 id="notes-answer-title" tabindex="-1">Answer from your notes</h2>
          <p id="notes-answer-query" class="tiny muted"></p>
          <p id="notes-answer-text"></p><div id="notes-sources"></div>
          <p class="tiny muted">Check the excerpts yourself: a valid source ID does not prove that every claim is supported. Answers leave this screen on refresh; the server keeps a copy for safe retries.</p>
        </section></section></div>`;
  const field = (id) => root.querySelector(`#${id}`);
  function syncControls() {
    root.querySelectorAll("input,textarea,button").forEach((n) => { n.disabled = Boolean(controller); });
    field("notes-stop").disabled = false;
    field("notes-stop").hidden = !controller;
    field("notes-previous").disabled = Boolean(controller) || page === 0;
    field("notes-next").disabled = Boolean(controller) || page + 1 >= totalPages;
    field("notes-index").disabled = Boolean(controller) || !selected || flow.indexes.has(selected.id);
  }
  async function run(message, action, timeout = 30000) {
    if (controller) return;
    const active = new AbortController(); controller = active;
    const timer = setTimeout(() => active.abort(), timeout);
    field("notes-error").hidden = true;
    field("notes-status").textContent = message;
    root.setAttribute("aria-busy", "true"); syncControls();
    try { await action(active.signal); }
    catch (error) {
      field("notes-error").textContent = active.signal.aborted
        ? "Stopped waiting. The server may still finish and use quota; retrying may make another request."
        : error instanceof TypeError ? "Could not reach the server. Your input has been kept." : error.message;
      field("notes-error").hidden = false;
      field("notes-status").textContent = "The request was not confirmed.";
    } finally {
      clearTimeout(timer); controller = null;
      root.setAttribute("aria-busy", "false"); syncControls();
    }
  }
  function choose(document, updateUrl = true) {
    selected = document;
    field("selected-title").textContent = document.title;
    field("selected-meta").textContent = `${document.filename} · Saved ${date(document.createdAt)}`;
    field("notes-content").hidden = false;
    field("notes-query").value = "";
    field("notes-answer").hidden = true;
    field("notes-error").hidden = true;
    const index = flow.indexes.get(document.id);
    field("notes-index-state").textContent = index
      ? `Indexed this visit · ${index.passageCount} passage(s). No need to index again.`
      : "Index once before asking. If indexed earlier, you can ask directly.";
    root.querySelectorAll("[data-note-id]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.noteId === document.id));
    });
    if (updateUrl) {
      const url = new URL(location.href); url.searchParams.set("document", document.id);
      history.replaceState(null, "", url);
    }
    syncControls();
  }
  async function loadPage(nextPage, signal) {
    const result = await client.list(nextPage, signal);
    page = nextPage; totalPages = result.totalPages;
    const list = field("notes-list"); list.replaceChildren();
    if (!result.content.length) list.append(el("p", "No study notes saved yet. Add your first document above.", "tiny muted"));
    for (const document of result.content) {
      const button = el("button", undefined, "notes-document"); button.type = "button";
      button.dataset.noteId = document.id;
      button.setAttribute("aria-pressed", String(selected?.id === document.id));
      button.append(el("strong", document.title), el("span", `${document.filename} · ${date(document.createdAt)}`, "tiny muted"));
      button.addEventListener("click", () => { if (!controller) choose(document); }); list.append(button);
    }
    field("notes-page").textContent = result.totalPages ? `${page + 1} / ${result.totalPages}` : "0 documents";
  }
  field("notes-upload").addEventListener("submit", (event) => {
    event.preventDefault();
    run("Saving notes…", async (signal) => {
      const document = await client.upload(field("notes-title").value, field("notes-file").files[0], signal);
      // Retain the uploaded document even if refreshing the list fails.
      choose(document); field("notes-upload").reset();
      field("notes-status").textContent = "Notes saved. Index them when you are ready to ask.";
      try { await loadPage(0, signal); }
      catch {
        field("notes-error").textContent = "Your notes were saved, but the list could not refresh. Use Refresh saved notes to reload it.";
        field("notes-error").hidden = false;
      }
    });
  });
  field("notes-index").addEventListener("click", () => {
    if (!selected) return;
    const document = selected;
    run("Creating and saving passage vectors…", async (signal) => {
      const index = await flow.index(document.id, signal);
      field("notes-index-state").textContent = `Indexed this visit · ${index.passageCount} passage(s). No need to index again.`;
      field("notes-status").textContent = "Notes indexed. You can now ask a question.";
    }, 90000);
  });
  field("notes-question").addEventListener("submit", (event) => {
    event.preventDefault(); if (!selected) return;
    const document = selected, query = field("notes-query").value;
    run("Finding passages and preparing an answer…", async (signal) => {
      const { result, cached } = await flow.answer(document.id, query, signal);
      field("notes-answer-title").textContent = result.insufficientEvidence ? "More evidence needed" : "Answer from your notes";
      field("notes-answer-query").textContent = `Question: ${result.query}`;
      field("notes-answer-text").textContent = result.answer;
      const sources = field("notes-sources"); sources.replaceChildren();
      if (result.sources.length) sources.append(el("h3", "Source excerpts"));
      for (const source of result.sources) {
        const details = el("details", undefined, "notes-source"); details.open = true;
        details.append(el("summary", `Passage ${source.number}`), el("p", source.text),
          el("small", `Source ID: ${source.passageId}`, "tiny muted"));
        sources.append(details);
      }
      field("notes-answer").hidden = false;
      field("notes-status").textContent = cached ? "Showing this visit’s saved answer. No new AI call." : "Answer ready.";
      field("notes-answer-title").focus();
    }, 120000);
  });
  field("notes-refresh").addEventListener("click", () => run("Refreshing saved notes…", async (signal) => {
    await loadPage(page, signal); field("notes-status").textContent = "Saved notes refreshed.";
  }));
  for (const [id, offset] of [["notes-previous", -1], ["notes-next", 1]]) {
    field(id).addEventListener("click", () => run("Loading saved notes…", async (signal) => {
      await loadPage(page + offset, signal); field("notes-status").textContent = "Saved notes loaded.";
    }));
  }
  field("notes-stop").addEventListener("click", () => controller?.abort());
  window.addEventListener("pagehide", () => controller?.abort(), { once: true });
  await run("Loading saved notes…", async (signal) => {
    await loadPage(0, signal);
    const id = new URL(location.href).searchParams.get("document");
    if (id) choose(await client.get(id, signal), false);
    field("notes-status").textContent = "Choose notes or save a new document.";
  });
}
