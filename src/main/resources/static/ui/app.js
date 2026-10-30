import { shell, wireShell, icon, empty, escape, brand } from "./components.js";
import { configureApi } from "./api-config.js";
import { applyMotionPreference, motionAllowed } from "./motion.js";
const page = document.body.dataset.page,
  site = document.getElementById("site");
applyMotionPreference();
if (page === "privacy") {
  site.innerHTML = `<div class="wrap"><header class="site-nav">${brand()}<a class="text-link" href="/dashboard.html">Back to workspace ${icon("arrow")}</a></header><main id="main-content" class="privacy-copy page-enter"><span class="eyebrow">YOUR DATA, EXPLAINED</span><h1>Privacy & your workspace.</h1><p>This describes the current project implementation. It is not a claim of a certified privacy or security program.</p><h2>A shared learning space</h2><p>Sign-in and user ownership are not implemented yet. Saved sessions, answers, feedback, and uploaded notes belong to the shared application database and are not isolated by user.</p><h2>What is stored</h2><p>The application saves submitted interview questions, answers, rubric feedback, scores, timestamps, documents, and passage indexes. Résumé uploads save their filename and extracted skill excerpts, plus a confirmed skill snapshot for each interview; original résumé files and full résumé text are not saved. Unsubmitted answer drafts are not saved. This browser stores only your motion preference.</p><h2>What goes to AI providers</h2><p>Résumé text is sent for skill extraction. Confirmed skill excerpts, interview selections, questions, answers, and selected feedback context are sent to the configured chat provider. If document embeddings are enabled, passage text and search queries are sent to the configured embedding service. Provider retention depends on that provider's policies.</p><h2>Current limits</h2><p>There are no account controls, self-service deletion/export tools, or protected user routes yet. Avoid putting sensitive personal information in this shared workspace. Provider API keys are used by the backend, not by the browser.</p><a class="btn secondary" href="/practice.html">Return to practice ${icon("arrow")}</a></main><footer class="site-footer"><span>Intervie · Current implementation</span><a href="/">Home</a></footer></div>`;
} else {
  site.innerHTML = shell(page, '<div id="page-root"></div>');
  wireShell();
  const root = document.getElementById("page-root");
  if (page === "settings") {
    root.innerHTML = `<div class="page-heading"><div><div class="eyebrow">MAKE SPACE FOR YOUR FOCUS</div><h1>Workspace settings.</h1><p>A few thoughtful controls for your learning environment.</p></div></div><div class="settings-list"><section class="panel setting-row"><div><h3>Ambient motion</h3><p>Enable decorative animation. Your device’s reduced-motion preference always takes priority.</p></div><input class="toggle-setting" type="checkbox" id="motion-toggle" aria-label="Enable ambient motion" ${motionAllowed() ? "checked" : ""}></section><p id="preference-status" role="status" class="tiny muted"></p><section class="panel"><div class="panel-head"><h3>Account & profile</h3><span class="pill">Planned</span></div><p class="tiny">Authentication, individual profiles, and private user histories are not available. This is a shared workspace; no protected account routes are claimed.</p></section><section class="panel"><div class="panel-head"><h3>Connected workspace</h3><span class="pill cyan">Spring Boot API</span></div><p class="tiny">The server supplies the public API address. Chat and database credentials stay on the backend. More account and preference controls will arrive with their backend support.</p><a class="text-link" href="/privacy.html">Read about your data ${icon("arrow")}</a></section></div>`;
    root.querySelector("#motion-toggle").addEventListener("change", (e) => {
      try {
        localStorage.setItem(
          "intervie-motion",
          e.target.checked ? "on" : "off",
        );
        applyMotionPreference();
        window.dispatchEvent(new Event("intervie-motion"));
        root.querySelector("#preference-status").textContent = motionAllowed()
          ? "Ambient motion enabled."
          : "Ambient motion disabled (or reduced by your device preference).";
      } catch {
        root.querySelector("#preference-status").textContent =
          "Your browser could not save this preference.";
      }
    });
  } else if (page === "questions") {
    root.innerHTML = `<div class="page-heading"><div><div class="eyebrow">A COLLECTION OF POSSIBILITIES</div><h1>Your question bank.</h1><p>A dedicated collection is on the roadmap.</p></div></div><section class="panel">${empty("Your past questions are already saved", "A searchable question-bank API is not implemented yet. You can browse every saved session and its questions in Practice History.", "/history.html", "Browse saved questions")}</section>`;
  } else {
    root.innerHTML =
      '<div class="skeleton" aria-label="Loading workspace"></div>';
    async function boot() {
      try {
        await configureApi();
        if (page === "practice") {
          await import("../resume-setup.js");
          root.replaceChildren(
            document.createElement("resume-interview-setup"),
          );
        } else if (page === "history") {
          await import("../history.js");
          root.replaceChildren(document.createElement("interview-history"));
        } else {
          const { renderDashboard } = await import("./dashboard.js");
          await renderDashboard(root, page);
        }
      } catch (error) {
        root.innerHTML = `<div class="error" role="alert"><h3>Workspace unavailable</h3><p>${escape(error instanceof TypeError ? "Could not reach the application. Open this page through the running Spring Boot server." : error.message)}</p><button class="btn secondary" id="retry-config">Retry connection</button></div>`;
        root.querySelector("button").addEventListener("click", boot);
      }
    }
    await boot();
  }
}
