import { brand, icon, lineChart, wireCharts } from "./components.js";
import { initMotion, startOrb } from "./motion.js";
if (new URL(location.href).searchParams.has("session"))
  location.replace("/practice.html" + location.search);
const sample = [4.5, 5.4, 5.1, 6.7, 6.2, 7.8, 7.4, 8.6].map((score, i) => ({
  score,
  label: `Sample week ${i + 1}`,
  short: `W${i + 1}`,
}));
document.getElementById("site").innerHTML =
  `<a class="skip" href="#main-content">Skip to content</a><div class="landing"><div class="wrap"><header class="site-nav">${brand()}<nav class="nav-links" aria-label="Main navigation"><a href="#features">The experience</a><a href="#how-it-works">How it works</a><a href="/dashboard.html">Dashboard</a><a class="btn secondary" href="/practice.html">Enter workspace ${icon("arrow")}</a></nav></header><main id="main-content"><section class="hero"><div class="hero-copy page-enter"><div class="eyebrow"><span class="dot"></span> Intelligence meets preparation</div><h1>Your next<br>interview.<br><em>Your best<br>performance.</em></h1><p>Upload your résumé, confirm your technical skills, and practise interviews built around your experience.</p><div class="actions"><a class="btn" href="/practice.html">Upload Your Résumé ${icon("arrow")}</a><a class="text-link" href="/dashboard.html">Explore Dashboard ${icon("chevron")}</a></div><div class="hero-foot"><span>BUILT AROUND YOUR RESUME</span><span>POWERED BY YOUR PROGRESS</span></div></div><div class="hero-art page-enter"><div class="orb-halo" aria-hidden="true"></div><canvas class="orb-canvas" aria-hidden="true"></canvas><div class="preview" data-tilt><div class="preview-top">${brand()}<span class="pill cyan">Illustrative preview</span></div><div class="preview-title"><strong>A better you. In progress.</strong>${icon("spark")}</div><div class="preview-metrics"><div class="preview-metric"><span>Readiness · concept</span><strong>86<span style="display:inline;font-size:12px">/100</span></strong><small>Sample only</small></div><div class="preview-metric"><span>Practice sessions</span><strong>24</strong><small>↗ 6 this week</small></div><div class="preview-metric"><span>Average score</span><strong>8.6</strong><small>out of 10</small></div></div><div class="preview-chart">${lineChart(sample, { demo: true, label: "Illustrative performance, not actual learner data" })}</div><div class="preview-bottom"><div><span class="muted tiny">Skill snapshot · sample</span>${[
    ["Python", 82],
    ["PostgreSQL", 74],
    ["Docker", 68],
  ]
    .map(
      ([name, n]) =>
        `<div class="mini-row"><span>${name}</span><b>${n}%</b></div><div class="meter"><i style="--fill:${n}%"></i></div>`,
    )
    .join(
      "",
    )}</div><div><span class="muted tiny">Recent practice · sample</span><div class="mini-row"><span>PostgreSQL</span><b class="accent">8.6 / 10</b></div><div class="mini-row"><span>Python</span><b>8.2 / 10</b></div><div class="mini-row"><span>PostgreSQL</span><b>7.8 / 10</b></div><p class="tiny muted">One question closer.<br>One answer stronger.</p></div></div></div><div class="float-card"><strong><span class="accent">${icon("spark")}</span> A little direction. A big difference.</strong><p>Sample feedback: “Your fundamentals are strong. Next, explain how an index changes query performance.”</p></div></div><div class="hero-bottom"><span>01 / YOUR NEXT CHAPTER</span><a href="#features">SCROLL TO EXPLORE ↓</a></div></section><section class="feature-section" id="features"><div class="section-index reveal">DESIGNED FOR THE WAY YOU GROW</div><div class="section-intro reveal"><h2>More than practice.<br>A clearer path forward.</h2><p>Make every answer useful. Understand what works, see what’s missing, and know where to go next.</p></div><div class="feature-grid">${[
    [
      "01",
      "chat",
      "AI Mock Interviews",
      "Upload your résumé, review extracted technical skills with source excerpts, and get questions based on the skills you confirm.",
    ],
    [
      "02",
      "target",
      "Smart Answer Evaluation",
      "See technical correctness, completeness, and clarity, with a reason behind every score.",
    ],
    [
      "03",
      "chart",
      "Performance Analytics",
      "Revisit answers, compare topic scores, and follow your saved practice. Readiness scoring is planned.",
    ],
    [
      "04",
      "spark",
      "Personalized Preparation",
      "Your latest feedback guides the next question toward your weakest rubric area.",
    ],
  ]
    .map(
      ([n, i, title, copy]) =>
        `<article class="feature-card reveal" data-tilt><span class="num">${n}</span><div class="feature-icon">${icon(i)}</div><h3>${title}</h3><p>${copy}</p></article>`,
    )
    .join(
      "",
    )}</div></section><section class="steps-section" id="how-it-works"><div class="section-index reveal">A SMALL HABIT. A REAL DIFFERENCE.</div><div class="section-intro reveal"><h2>Find your rhythm.<br>Build your confidence.</h2><p>No elaborate setup. Just a focused space to think, answer, and improve.</p></div><div class="steps reveal">${[
    [
      "01",
      "Upload. Review. Confirm.",
      "Upload DOCX/TXT/MD or paste résumé text. Review the skills found and choose your interview difficulty.",
    ],
    [
      "02",
      "Think. Answer. Discover.",
      "Answer questions about your confirmed résumé skills in a focused workspace. Feedback guides the next question.",
    ],
    [
      "03",
      "Make the next answer better.",
      "Review your feedback, track your statistics, and practise a weakness.",
    ],
  ]
    .map(
      ([n, t, p]) =>
        `<article class="step"><div class="step-number">${n}</div><h3>${t}</h3><p>${p}</p></article>`,
    )
    .join(
      "",
    )}</div></section><section class="statistics-section" id="statistics"><div class="section-index reveal">PROGRESS, MADE VISIBLE</div><div class="section-intro reveal"><h2>Small steps.<br>Meaningful momentum.</h2><p><span class="pill violet">Demonstration data</span><br><br>These numbers illustrate the experience. Your dashboard uses your saved data; readiness and active practice-time tracking are not available yet.</p></div><div class="stats-preview"><div class="demo-stats reveal">${[
    ["24", "Interviews completed"],
    ["8.6", "Average score / 10"],
    ["128", "Questions answered"],
    ["3.5", "Weekly practice hours · concept"],
  ]
    .map(
      ([n, label]) =>
        `<div class="demo-stat"><strong data-count="${n}">${n}</strong><span>${label}</span></div>`,
    )
    .join(
      "",
    )}</div><div class="panel reveal"><div class="panel-head"><h3>Improvement over time</h3><span class="pill">Sample · 8 weeks</span></div>${lineChart(sample, { demo: true })}</div></div></section><section class="final-cta reveal"><div class="eyebrow"><span class="dot"></span> Your potential is a work in progress</div><h2>Turn every practice<br>session into progress.</h2><p>Meet the next version of yourself. One answer at a time.</p><a class="btn" href="/practice.html">Start Your Preparation ${icon("arrow")}</a></section></main><footer class="site-footer">${brand()}<span>A thoughtful space for your next chapter.</span><div class="footer-links"><a href="/dashboard.html">Workspace</a><a href="/privacy.html">Privacy</a><a href="#features">Product</a></div></footer></div></div>`;
initMotion();
wireCharts();
startOrb(document.querySelector("canvas"));
