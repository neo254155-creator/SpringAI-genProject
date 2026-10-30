const paths = {
  spark: "M12 3l2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4Z",
  arrow: "M5 12h14m-6-6 6 6-6 6",
  grid: "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z",
  play: "m8 5 11 7-11 7Z",
  history: "M3 11a9 9 0 1 1 2 7M3 4v7h7m2-4v6l4 2",
  chart: "M4 4v16h17M7 14l4-5 4 3 5-7",
  book: "M12 5v15M3 4c4-1 6 0 9 2 3-2 5-3 9-2v15c-4-1-6 0-9 2-3-2-5-3-9-2Z",
  target: "M21 12a9 9 0 1 1-9-9m0 4a5 5 0 1 0 5 5m-5 0 9-9m-5 0h5v5",
  settings: "M4 7h16M4 17h16M9 4v6m6 4v6",
  menu: "M4 6h16M4 12h16M4 18h16",
  close: "m6 6 12 12M6 18 18 6",
  panel: "M3 4h18v16H3ZM9 4v16m6-11-3 3 3 3",
  clock: "M12 8v5l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0",
  check: "m5 12 4 4L19 6",
  code: "m8 7-5 5 5 5m8-10 5 5-5 5m-3-12-2 14",
  trend: "m3 17 6-6 4 3 8-10m-6 0h6v6",
  shield: "m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z",
  chat: "M21 11a8 8 0 0 1-8 8H6l-4 3V11a9 9 0 0 1 19 0ZM7 10h10m-10 4h6",
  refresh: "M20 7a9 9 0 0 0-16 2m0-5v5h5m-5 8a9 9 0 0 0 16-2m0 5v-5h-5",
  chevron: "m9 5 7 7-7 7",
  globe:
    "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18",
};
export function icon(name, cls = "") {
  return `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name] || paths.spark}"/></svg>`;
}
export function brand() {
  return `<a class="brand" href="/" aria-label="Intervie home">${icon("spark", "brand-mark")}<span class="brand-word">intervie<span class="accent">.</span></span></a>`;
}
export function el(tag, text, cls) {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
}
export function escape(value) {
  return String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
}
export function date(value, options = { month: "short", day: "numeric" }) {
  const d = new Date(value);
  return value && Number.isFinite(d.getTime())
    ? d.toLocaleDateString(undefined, options)
    : "—";
}
export function empty(
  title,
  description,
  href = "/practice.html",
  label = "Start practicing",
) {
  return `<div class="empty">${icon("spark")}<h3>${escape(title)}</h3><p>${escape(description)}</p>${href ? `<a class="btn secondary" href="${escape(href)}">${escape(label)} ${icon("arrow")}</a>` : ""}</div>`;
}
const links = [
  ["overview", "Overview", "grid", "/dashboard.html"],
  ["practice", "Résumé Interview", "chat", "/practice.html"],
  ["history", "Practice History", "history", "/history.html"],
  ["analytics", "Performance Analytics", "chart", "/analytics.html"],
  ["questions", "Question Bank", "book", "/questions.html"],
  ["recommendations", "Recommendations", "spark", "/recommendations.html"],
  ["settings", "Settings", "settings", "/settings.html"],
];
export function shell(page, content) {
  const label = links.find((x) => x[0] === page)?.[1] || "Workspace";
  return `<a class="skip" href="#main-content">Skip to content</a><div class="app-shell"><button class="mobile-backdrop" aria-label="Close navigation" tabindex="-1"></button><aside class="sidebar" id="sidebar" aria-label="Workspace navigation">${brand()}<span class="eyebrow">Your workspace</span><nav class="side-nav">${links.map(([id, label, name, href]) => `<a class="side-link ${id === page ? "active" : ""}" href="${href}" ${id === page ? 'aria-current="page"' : ""} title="${label}">${icon(name)}<span class="nav-label">${label}</span></a>`).join("")}</nav><div class="sidebar-bottom"><div class="sidebar-note"><span class="pill cyan">${icon("spark")} A little, every day.</span><p>Your next breakthrough starts with one good question.</p></div><div class="side-profile"><div class="avatar">${icon("code")}</div><div>Learning workspace<br><small>Shared · no sign-in yet</small></div></div></div></aside><div class="app-body"><header class="topbar"><div class="breadcrumb"><button class="icon-btn desktop-collapse" id="collapse-nav" aria-label="Collapse sidebar" aria-expanded="true" aria-controls="sidebar">${icon("panel")}</button><button class="icon-btn mobile-menu" id="open-nav" aria-label="Open navigation" aria-expanded="false" aria-controls="sidebar">${icon("menu")}</button><span>Workspace</span><span>/</span><span>${label}</span></div><div class="top-actions"><span class="pill">${icon("globe")} Shared workspace</span><a class="text-link" href="/privacy.html">${icon("shield")} Privacy</a><a class="avatar" href="/settings.html" aria-label="Workspace settings">${icon("settings")}</a></div></header><main class="app-content page-enter" id="main-content">${content}</main></div></div>`;
}
export function wireShell() {
  const root = document.querySelector(".app-shell");
  if (!root) return;
  const button = document.getElementById("open-nav"),
    sidebar = document.getElementById("sidebar");
  function close() {
    root.classList.remove("menu-open");
    button.setAttribute("aria-expanded", "false");
    if (innerWidth <= 600) sidebar.inert = true;
    button.focus();
  }
  function resize() {
    sidebar.inert = innerWidth <= 600 && !root.classList.contains("menu-open");
  }
  resize();
  window.addEventListener("resize", resize);
  button.addEventListener("click", () => {
    root.classList.add("menu-open");
    sidebar.inert = false;
    button.setAttribute("aria-expanded", "true");
    sidebar.querySelector("a").focus();
  });
  document.querySelector(".mobile-backdrop").addEventListener("click", close);
  document.addEventListener("keydown", (e) => {
    if (!root.classList.contains("menu-open") || innerWidth > 600) return;
    if (e.key === "Escape") close();
    if (e.key === "Tab") {
      const items = [...sidebar.querySelectorAll("a,button")];
      if (e.shiftKey && document.activeElement === items[0]) {
        e.preventDefault();
        items.at(-1).focus();
      } else if (!e.shiftKey && document.activeElement === items.at(-1)) {
        e.preventDefault();
        items[0].focus();
      }
    }
  });
  document.getElementById("collapse-nav").addEventListener("click", (e) => {
    const small = root.classList.toggle("collapsed");
    e.currentTarget.setAttribute("aria-expanded", String(!small));
    e.currentTarget.setAttribute(
      "aria-label",
      small ? "Expand sidebar" : "Collapse sidebar",
    );
  });
}
let chartId = 0;
export function lineChart(
  points,
  { label = "Answer scores over time", demo = false } = {},
) {
  if (!points.length)
    return empty(
      "Your progress starts here",
      "Submit an answer to see your scores over time.",
    );
  const id = `chart-${++chartId}`,
    w = 620,
    h = 230,
    left = 34,
    right = 12,
    top = 18,
    bottom = 32;
  const coords = points.map((p, i) => ({
    x:
      left +
      (points.length === 1 ? 0.5 : i / (points.length - 1)) *
        (w - left - right),
    y: top + (1 - p.score / 10) * (h - top - bottom),
    ...p,
  }));
  const d = coords
    .map((p, i) => `${i ? "L" : "M"}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    .join(" ");
  const labels = [0, 5, 10]
    .map((v) => {
      const y = top + (1 - v / 10) * (h - top - bottom);
      return `<line class="chart-grid" x1="${left}" x2="${w - right}" y1="${y}" y2="${y}"/><text x="8" y="${y + 4}">${v}</text>`;
    })
    .join("");
  return `<div class="chart-container"><div class="chart-legend"><span><i class="legend-dot"></i>${demo ? "Sample performance" : "Latest saved answer per session"}</span><span>Score / 10</span></div><svg viewBox="0 0 ${w} ${h}" class="chart" role="img" aria-labelledby="${id}-title"><title id="${id}-title">${escape(label)}</title><defs><linearGradient id="${id}-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#b29afa" stop-opacity=".22"/><stop offset="100%" stop-color="#b29afa" stop-opacity="0"/></linearGradient></defs>${labels}<path d="${d} L${coords.at(-1).x},${h - bottom} L${coords[0].x},${h - bottom} Z" fill="url(#${id}-fill)"/><path class="chart-line" d="${d}"/>${coords.map((p, i) => `<circle class="chart-dot" cx="${p.x}" cy="${p.y}" r="4" tabindex="0" role="img" aria-label="${escape(p.label)}: ${p.score} out of 10" data-tooltip="${escape(p.label)} · ${p.score}/10"><title>${escape(p.label)}: ${p.score}/10</title></circle>${i === 0 || i === coords.length - 1 || i === Math.floor(coords.length / 2) ? `<text x="${p.x}" y="${h - 5}" text-anchor="${i === 0 ? "start" : i === coords.length - 1 ? "end" : "middle"}">${escape(p.short || date(p.time))}</text>` : ""}`).join("")}</svg><div class="chart-tooltip" aria-live="polite">Hover or focus a point to explore.</div><details class="tiny muted"><summary>View chart data</summary><table><thead><tr><th>Date / session</th><th>Score /10</th></tr></thead><tbody>${points.map((p) => `<tr><td>${escape(p.label)}</td><td>${p.score}</td></tr>`).join("")}</tbody></table></details></div>`;
}
export function wireCharts(root = document) {
  root.querySelectorAll("[data-tooltip]").forEach((dot) => {
    for (const event of ["mouseenter", "focus"])
      dot.addEventListener(event, () => {
        dot
          .closest(".chart-container")
          .querySelector(".chart-tooltip").textContent = dot.dataset.tooltip;
      });
  });
}
