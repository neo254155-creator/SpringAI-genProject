import { currentAccount, signOut } from "./auth-client.js";
import { shell, wireShell, icon, empty, escape, brand } from "./components.js";
import { configureApi } from "./api-config.js";
import { applyMotionPreference, motionAllowed } from "./motion.js";
const page = document.body.dataset.page,
  site = document.getElementById("site");
applyMotionPreference();
if (page === "privacy") {
  site.innerHTML = `<div class="wrap"><header class="site-nav">${brand()}<a class="text-link" href="/dashboard.html">Back to workspace ${icon("arrow")}</a></header><main id="main-content" class="privacy-copy page-enter"><span class="eyebrow">YOUR DATA, EXPLAINED</span><h1>Privacy & your workspace.</h1><p>This describes the current project implementation. It is not a claim of a certified privacy or security program.</p><h2>Your account workspace</h2><p>Sign-in protects your résumés, sessions, answers, feedback, notes, and progress from other accounts. The first account, created directly from localhost, receives the existing project history once. Later accounts start with their own workspace.</p><h2>What is stored</h2><p>The application saves submitted interview questions, answers, rubric feedback, scores, timestamps, documents, and passage indexes. Résumé uploads save their filename and extracted skill excerpts, plus a confirmed skill snapshot for each interview; original résumé files and full résumé text are not saved. Unsubmitted answer drafts are not saved. The server retains request-result copies for safe retries, including generated note answers. This browser stores your motion preference and, per tab, random retry keys and input hashes; retry storage contains no résumé text or answers.</p><h2>What goes to AI providers</h2><p>Résumé text is sent for skill extraction. Confirmed skill excerpts, interview selections, questions, answers, and selected feedback context are sent to the configured chat provider. If document embeddings are enabled, passage text and search queries are sent to the configured embedding service. Provider retention depends on that provider's policies.</p><h2>Current limits</h2><p>Password reset, multi-factor authentication, and self-service deletion/export are not implemented. Sessions expire after inactivity. Local sessions clear on restart; the production profile stores sessions in the database. Use HTTPS for deployment; the server and database administrators can access stored records. Provider API keys are used by the backend, not by the browser.</p><a class="btn secondary" href="/practice.html">Return to practice ${icon("arrow")}</a></main><footer class="site-footer"><span>Intervie · Current implementation</span><a href="/">Home</a></footer></div>`;
} else {
  try {
  await configureApi();
  const account = await currentAccount();
  site.innerHTML = shell(page, '<div id="page-root"></div>');
  wireShell();
  document.getElementById("account-name").textContent = account.username;
  document.getElementById("sign-out").addEventListener("click", async () => { try { await signOut(); } catch (error) { alert(error.message); } });
  const root = document.getElementById("page-root");
  if (page === "settings") {
    root.innerHTML = `<div class="page-heading"><div><div class="eyebrow">MAKE SPACE FOR YOUR FOCUS</div><h1>Workspace settings.</h1><p>A few thoughtful controls for your learning environment.</p></div></div><div class="settings-list"><section class="panel setting-row"><div><h3>Ambient motion</h3><p>Enable decorative animation. Your device’s reduced-motion preference always takes priority.</p></div><input class="toggle-setting" type="checkbox" id="motion-toggle" aria-label="Enable ambient motion" ${motionAllowed() ? "checked" : ""}></section><p id="preference-status" role="status" class="tiny muted"></p><section class="panel"><div class="panel-head"><h3>Account & profile</h3><span class="pill cyan">Signed in</span></div><p class="tiny">Your résumé interviews, notes, and progress belong to your account. Use Sign out when you finish on a shared computer.</p></section><section class="panel"><div class="panel-head"><h3>Connected workspace</h3><span class="pill cyan">Spring Boot API</span></div><p class="tiny">The server supplies the public API address. Chat and database credentials stay on the backend. More account and preference controls will arrive with their backend support.</p><a class="text-link" href="/privacy.html">Read about your data ${icon("arrow")}</a></section></div>`;
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

        if (page === "practice") {
          await import("../resume-setup.js");
          root.replaceChildren(
            document.createElement("resume-interview-setup"),
          );
        } else if (page === "history") {
          await import("../history.js");
          root.replaceChildren(document.createElement("interview-history"));
        } else if (page === "documents") {
          const { renderDocuments } = await import("./documents.js");
          await renderDocuments(root);
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
  } catch (error) {
    site.innerHTML = `<main class="wrap"><div class="error" role="alert"><h1>Workspace unavailable</h1><p>${escape(error.message)}</p><button class="btn" id="retry-account">Retry connection</button></div></main>`;
    document.getElementById("retry-account").addEventListener("click", () => location.reload());
  }
}
