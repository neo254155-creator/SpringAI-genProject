import { DashboardClient } from "./dashboard-client.js";
import { buildAnalytics } from "./analytics.js";
import {
  icon,
  escape,
  date,
  empty,
  lineChart,
  wireCharts,
} from "./components.js";
import { initMotion } from "./motion.js";
const headings = {
  overview: [
    "YOUR LEARNING, IN FOCUS",
    "Your résumé. Your next chapter.",
    "Build your interview from your own technical skills, then follow your progress.",
  ],
  analytics: [
    "THE BIGGER PICTURE",
    "Make your progress visible.",
    "Follow your scores, understand your strengths, and find your next focus.",
  ],
  recommendations: [
    "YOUR NEXT MOVE",
    "Practice with intention.",
    "Suggestions based on your saved topic scores.",
  ],
};
function metric(label, value, note, name) {
  return `<article class="panel stat-card"><div class="stat-label">${label}${icon(name)}</div><div class="stat-value">${value}</div><div class="stat-note">${note}</div></article>`;
}
function skillsView(skills, resume) {
  if (!resume)
    return empty(
      "Your skills start with your résumé",
      "Upload your résumé and confirm its technical skills. There is no preset technology list.",
      "/practice.html",
      "Upload résumé",
    );
  return (
    resume.skills
      .map(({ name }) => {
        const topic = skills.find(
          (s) => s.topic.toLowerCase() === name.toLowerCase(),
        );
        return `<div class="skill-row"><header><span>${escape(name)}</span><small>${topic ? `${topic.score.toFixed(1)} / 10` : "Not practised yet"}</small></header><div class="meter">${topic ? `<i style="--fill:${topic.score * 10}%"></i>` : ""}</div></div>`;
      })
      .join("") +
    `<p class="data-note">Confirmed skills from ${escape(resume.filename)}. Scores use saved attempts; extraction does not imply proficiency.</p>`
  );
}
function recommendationView(rows, resume) {
  if (!resume)
    return empty(
      "Start with your résumé",
      "Upload and review your skills to make this interview yours.",
      "/practice.html",
      "Upload résumé",
    );
  const relevant = rows.filter((row) =>
    resume.skills.some((s) => s.name.toLowerCase() === row.topic.toLowerCase()),
  );
  if (!relevant.length)
    return empty(
      "Your confirmed skills are ready",
      "Start an interview to get feedback and find which skills need practice.",
      `/practice.html?resume=${encodeURIComponent(resume.resumeId)}`,
      "Start résumé interview",
    );
  return relevant
    .map(
      (r, i) =>
        `<article class="recommendation"><span class="eyebrow" style="font-size:9px;margin-bottom:10px">0${i + 1} / RESUME SKILL PRACTICE</span><h3>Revisit ${escape(r.topic)}</h3><p>Your ${escape(r.level)} average is ${r.averageScore.toFixed(1)}/10 across ${r.attempts} saved attempts. Review your missing points and practise this confirmed skill.</p><a class="text-link accent" href="/practice.html?resume=${encodeURIComponent(resume.resumeId)}&skill=${encodeURIComponent(r.topic)}&level=${encodeURIComponent(r.level)}">Review skills & practise ${icon("arrow")}</a></article>`,
    )
    .join("");
}
function resumeEntry(resume) {
  return `<section class="panel resume-entry"><div><span class="eyebrow">YOUR RESUME. YOUR INTERVIEW.</span><h2>${resume ? "Your experience sets the questions." : "Start with your résumé."}</h2><p>${resume ? `Confirmed skills: ${resume.skills.map((s) => escape(s.name)).join(" · ")}` : "Upload your résumé → review extracted technical skills → choose difficulty → answer questions based on your skills."}</p><span class="tiny muted">DOCX, TXT/MD, or paste text · PDF parsing is not supported yet</span></div><div class="actions"><a class="btn" href="/practice.html">${icon("book")} Upload résumé</a>${resume ? `<a class="btn secondary" href="/practice.html?resume=${encodeURIComponent(resume.resumeId)}">Use saved skills ${icon("arrow")}</a>` : ""}</div></section>`;
}
function elapsed(s) {
  if (!s.completedAt) return "In progress";
  const n = (new Date(s.completedAt) - new Date(s.createdAt)) / 60000;
  return Number.isFinite(n) && n >= 0
    ? `${Math.max(1, Math.round(n))} min elapsed`
    : "—";
}
function sessionsView(page, resumes) {
  if (!page.content.length)
    return empty(
      "Your résumé interview starts here",
      "Upload your résumé to build your skill-based interview history.",
    );
  const map = new Map(resumes.map((r) => [r.session.id, r]));
  return `<div class="table-wrap"><table><thead><tr><th>Interview</th><th>Date</th><th>Latest answer</th><th>Status / elapsed</th><th><span class="sr-only">Review</span></th></tr></thead><tbody>${page.content
    .map((s) => {
      const r = map.get(s.id),
        score = r?.answer?.feedback?.score;
      return `<tr><td><strong>${s.resume ? "Résumé interview" : escape(s.topic)}</strong><br><span class="tiny">${escape(s.level)} · ${s.resume ? escape(s.resume.skills.map((skill) => skill.name).join(", ")) : escape(s.track)}</span></td><td>${date(s.createdAt)}</td><td>${score === undefined ? "—" : `<strong class="accent">${score}/10</strong>`}</td><td><span class="pill ${s.status === "ACTIVE" ? "violet" : "cyan"}">${s.status === "ACTIVE" ? "Active" : "Completed"}</span><br><span class="tiny">${elapsed(s)}</span></td><td><a class="btn secondary" href="/practice.html?session=${encodeURIComponent(s.id)}">${s.status === "ACTIVE" ? "Resume" : "Review"} ${icon("arrow")}</a></td></tr>`;
    })
    .join(
      "",
    )}</tbody></table></div><p class="data-note">Latest ${page.content.length} of ${page.totalElements} sessions. Score is the latest answer, not a session average. Elapsed time includes breaks.</p>`;
}
function activityView(days) {
  const max = Math.max(1, ...days.map((d) => d.count));
  return `<div class="activity-grid">${days.map((d) => `<div class="activity-day"><b>${d.count}</b><i style="--height:${Math.max(3, (d.count / max) * 100)}px" title="${date(d.date)}: ${d.count} sessions started"></i>${date(d.date, { weekday: "short" })}</div>`).join("")}</div><p class="data-note">Sessions started in the last 7 days, within the latest 10 sessions. Active practice time is not tracked.</p>`;
}
export async function renderDashboard(root, page) {
  const [eyebrow, title, description] = headings[page];
  root.innerHTML = `<div class="page-heading"><div><div class="eyebrow">${eyebrow}</div><h1>${title}</h1><p>${description}</p></div><a class="btn" href="/practice.html">${icon("play")} Résumé interview</a></div><div id="dashboard-data" aria-live="polite"><div class="skeleton-grid" aria-label="Loading your statistics">${Array(4).fill('<div class="skeleton"></div>').join("")}</div><p class="data-note">Loading your saved practice…</p></div>`;
  const body = root.querySelector("#dashboard-data");
  let controller;
  async function load() {
    controller?.abort();
    controller = new AbortController();
    const current = controller;
    const signal = controller.signal;
    const timer = setTimeout(() => current.abort(), 30000);
    body.setAttribute("aria-busy", "true");
    try {
      const data = await new DashboardClient().load(signal);
      if (controller !== current) return;
      signal.throwIfAborted();
      const a = buildAnalytics(data.progress, data.page.content, data.resumes);
      const resume =
        data.page.content.find((session) => session.resume)?.resume || null;
      const scopedRecommendations = resume
        ? [...data.progress]
            .filter((row) =>
              resume.skills.some(
                (skill) => skill.name.toLowerCase() === row.topic.toLowerCase(),
              ),
            )
            .sort((a, b) => a.averageScore - b.averageScore)
            .slice(0, 3)
        : [];
      const notice = data.partial
        ? '<div class="status-banner">Some session details could not load. Totals are available; recent scores may be incomplete. <button class="icon-btn" id="retry-partial" aria-label="Retry session details">' +
          icon("refresh") +
          "</button></div>"
        : "";
      const metrics = `<div class="stat-grid">${metric("Total interviews", data.page.totalElements, "All saved sessions", "chat")}${metric("Average score", a.totals.averageScore === null ? "—" : `${a.totals.averageScore.toFixed(1)}<span class="tiny muted"> / 10</span>`, "All saved answer attempts", "target")}${metric("Questions answered", a.totals.questionsAnswered, "Distinct saved questions", "check")}${metric("Recent score change", a.trend === null ? "—" : `${a.trend > 0 ? "+" : ""}${a.trend.toFixed(1)}`, "First → last displayed answer /10", "trend")}</div>`;
      const performance = `<section class="panel"><div class="panel-head"><div><h3>Performance over time</h3><p>A little perspective on your progress.</p></div><span class="pill violet">Recent sessions</span></div>${lineChart(a.points)}<p class="data-note">Latest answer from each of the 10 most recent sessions; not the complete answer history.</p></section>`;
      const skills = `<section class="panel"><div class="panel-head"><h3>Your résumé skills</h3>${icon("target")}</div>${skillsView(a.skills, resume)}<p class="data-note">Topic averages from saved attempts, not a mastery or readiness score.</p></section>`;
      if (page === "recommendations") {
        body.innerHTML =
          notice +
          `<div class="dashboard-grid"><section class="panel"><div class="panel-head"><h3>Your practice priorities</h3><span class="pill cyan">Based on your scores</span></div>${recommendationView(scopedRecommendations, resume)}</section><section class="panel"><span class="eyebrow">HOW IT WORKS</span><h2 style="font-size:28px">Let your answers<br>set the direction.</h2><p>Confirmed résumé skills with lower saved averages appear first. Within an interview, your latest rubric feedback guides the next question.</p><div class="status-banner">These suggestions use a transparent score-based rule. An AI recommendations API and study-task tools are planned.</div><a class="text-link" href="/history.html">Explore your feedback ${icon("arrow")}</a></section></div>`;
      } else if (page === "analytics") {
        body.innerHTML =
          notice +
          (page === "overview" ? resumeEntry(resume) : "") +
          metrics +
          `<div class="dashboard-grid">${performance}${skills}<section class="panel"><div class="panel-head"><h3>Weekly practice activity</h3><span class="pill">Sessions started</span></div>${activityView(a.days)}</section><section class="panel"><div class="panel-head"><h3>Strongest & weakest topics</h3>${icon("chart")}</div>${a.skills.length ? `<p><span class="accent">Highest average</span><br>${escape([...a.skills].sort((x, y) => y.score - x.score)[0].topic)}</p><p><span style="color:var(--violet)">Lowest average</span><br>${escape([...a.skills].sort((x, y) => x.score - y.score)[0].topic)}</p><p class="data-note">${a.skills.length === 1 ? "Only one assessed topic; no cross-topic comparison yet." : "Weighted by saved attempt counts."}</p>` : empty("No topic scores yet", "Submit an answer to compare your topics.")}<div class="divider"></div><p class="data-note">Questions come from confirmed résumé skills. Behavioral/HR interview modes remain planned.</p></section><section class="panel wide"><div class="panel-head"><h3>Interview completion history</h3><a class="text-link" href="/history.html">Full history ${icon("arrow")}</a></div>${sessionsView(data.page, data.resumes)}</section></div>`;
      } else {
        body.innerHTML =
          notice +
          (page === "overview" ? resumeEntry(resume) : "") +
          metrics +
          `<div class="dashboard-grid">${performance}${skills}<section class="panel"><div class="panel-head"><h3>Recent sessions</h3><a class="text-link" href="/history.html">View all ${icon("arrow")}</a></div>${sessionsView(data.page, data.resumes)}</section><section class="panel"><div class="panel-head"><h3>Your next focus</h3>${icon("spark")}</div>${recommendationView(scopedRecommendations.slice(0, 2), resume)}<p class="data-note">Score-based suggestions · AI recommendations planned.</p></section></div>`;
      }
      body.querySelector("#retry-partial")?.addEventListener("click", load);
      wireCharts(root);
      initMotion(root);
    } catch (error) {
      if (!root.isConnected || controller !== current) return;
      body.innerHTML = `<div class="error" role="alert"><h3>We couldn’t load your practice.</h3><p>${escape(signal.aborted ? "The request timed out. Please try again." : error instanceof TypeError ? "The server could not be reached. Check that the application is running." : error.message)}</p><button class="btn secondary" id="retry-dashboard">${icon("refresh")} Try again</button></div>`;
      body.querySelector("button").addEventListener("click", load);
    } finally {
      clearTimeout(timer);
      if (controller === current) body.setAttribute("aria-busy", "false");
    }
  }
  window.addEventListener("pagehide", () => controller?.abort(), {
    once: true,
  });
  await load();
}
