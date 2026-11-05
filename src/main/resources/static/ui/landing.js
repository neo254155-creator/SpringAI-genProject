import { brand, icon } from "./components.js";
import { initMotion, startOrb } from "./motion.js";
if (new URL(location.href).searchParams.has("session"))
  location.replace("/practice.html" + location.search);
document.getElementById("site").innerHTML =
  `<a class="skip" href="#main-content">Skip to content</a><div class="landing"><div class="wrap"><header class="site-nav">${brand()}<nav class="nav-links" aria-label="Main navigation"><a href="#features">The experience</a><a href="#how-it-works">How it works</a><a href="/dashboard.html">Dashboard</a><a class="btn secondary" href="/practice.html">Enter workspace ${icon("arrow")}</a></nav></header><main id="main-content"><section class="hero"><div class="hero-copy page-enter"><h1>Your next<br>interview.<br><em>Your best<br>performance.</em></h1><p>Upload your résumé, confirm your technical skills, and practise interviews built around your experience.</p><div class="actions"><a class="btn" href="/practice.html">Upload Your Résumé ${icon("arrow")}</a><a class="text-link" href="/dashboard.html">Explore Dashboard ${icon("chevron")}</a></div></div><div class="hero-art page-enter"><div class="orb-halo" aria-hidden="true"></div><canvas class="orb-canvas" aria-hidden="true"></canvas><div class="preview"><div class="preview-top">${brand()}</div><div class="preview-title"><strong>Your interview practice</strong></div><p>Work through questions based on your résumé.</p><ol class="practice-outline"><li><strong>Upload your résumé</strong><p>Review and confirm the extracted skills.</p></li><li><strong>Choose your difficulty</strong><p>Answer questions about your experience.</p></li><li><strong>Review your feedback</strong><p>See your scores and practise weaker areas.</p></li><li><strong>Track your progress</strong><p>Return to your saved interviews and answers.</p></li></ol><a class="text-link" href="/practice.html">Start an interview ${icon("arrow")}</a></div></div><div class="hero-bottom"><span>INTERVIEW PRACTICE</span><a href="#features">SCROLL TO EXPLORE ↓</a></div></section><section class="feature-section" id="features"><div class="section-index reveal">WHAT YOU CAN DO</div><div class="section-intro reveal"><h2>Practise. Get feedback.<br>Try again.</h2><p>Make every answer useful. Understand what works, see what’s missing, and know where to go next.</p></div><div class="feature-grid">${[
    [
      "01",
      "chat",
      "Résumé Interviews",
      "Upload your résumé, review extracted technical skills with source excerpts, and get questions based on the skills you confirm.",
    ],
    [
      "02",
      "target",
      "Answer Feedback",
      "See technical correctness, completeness, and clarity, with a reason behind every score.",
    ],
    [
      "03",
      "chart",
      "Performance Analytics",
      "Revisit answers, compare topic scores, and follow your saved practice. ",
    ],
    [
      "04",
      "spark",
      "Weakness Practice",
      "Your latest feedback guides the next question toward your weakest rubric area.",
    ],
  ]
    .map(
      ([n, i, title, copy]) =>
        `<article class="feature-card reveal" data-tilt><span class="num">${n}</span><div class="feature-icon">${icon(i)}</div><h3>${title}</h3><p>${copy}</p></article>`,
    )
    .join(
      "",
    )}</div></section><section class="steps-section" id="how-it-works"><div class="section-index reveal">HOW IT WORKS</div><div class="section-intro reveal"><h2>From résumé<br>to interview practice.</h2><p>Upload your résumé, answer questions, and review your feedback.</p></div><div class="steps reveal">${[
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
    )}</div></section><section class="final-cta reveal"><h2>Turn every practice<br>session into progress.</h2><p>Pick up a saved interview or start a new one.</p><a class="btn" href="/practice.html">Start Your Preparation ${icon("arrow")}</a></section></main><footer class="site-footer">${brand()}<span>Résumé-based interview practice.</span><div class="footer-links"><a href="/dashboard.html">Workspace</a><a href="/privacy.html">Privacy</a><a href="#features">Product</a></div></footer></div></div>`;
initMotion();

startOrb(document.querySelector("canvas"));
