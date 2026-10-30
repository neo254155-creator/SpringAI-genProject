export function motionAllowed() {
  try {
    return (
      !matchMedia("(prefers-reduced-motion: reduce)").matches &&
      localStorage.getItem("intervie-motion") !== "off"
    );
  } catch {
    return !matchMedia("(prefers-reduced-motion: reduce)").matches;
  }
}
export function applyMotionPreference() {
  let enabled = motionAllowed();
  document.documentElement.dataset.motion = enabled ? "on" : "off";
  return enabled;
}
export function initMotion(root = document) {
  applyMotionPreference();
  const elements = root.querySelectorAll(".reveal");
  if (!motionAllowed() || !("IntersectionObserver" in window)) {
    elements.forEach((e) => e.classList.add("visible"));
    return;
  }
  document.documentElement.classList.add("motion-ready");
  const observer = new IntersectionObserver(
    (entries) =>
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          entry.target.querySelectorAll("[data-count]").forEach(countUp);
          observer.unobserve(entry.target);
        }
      }),
    { threshold: 0.1 },
  );
  elements.forEach((e) => observer.observe(e));
  const meters = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        meters.unobserve(entry.target);
      }
    });
  });
  root.querySelectorAll(".meter").forEach((meter) => meters.observe(meter));
  if (matchMedia("(pointer:fine)").matches)
    root.querySelectorAll("[data-tilt]").forEach((card) => {
      card.addEventListener("pointermove", (e) => {
        if (!motionAllowed()) return;
        const r = card.getBoundingClientRect();
        card.style.transform = `perspective(1000px) rotateX(${-(e.clientY - r.top - r.height / 2) / 65}deg) rotateY(${(e.clientX - r.left - r.width / 2) / 65}deg)`;
      });
      card.addEventListener("pointerleave", () => (card.style.transform = ""));
    });
}
function countUp(node) {
  const end = Number(node.dataset.count),
    start = performance.now();
  const decimals = String(end).includes(".") ? 1 : 0;
  function draw(now) {
    const t = Math.min((now - start) / 1000, 1);
    node.textContent = (end * (1 - (1 - t) ** 3)).toFixed(decimals);
    if (t < 1 && motionAllowed()) requestAnimationFrame(draw);
    else node.textContent = end.toFixed(decimals);
  }
  requestAnimationFrame(draw);
}
export function startOrb(canvas) {
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const media = matchMedia("(prefers-reduced-motion: reduce)");
  let frame = 0,
    visible = true;
  const dots = Array.from({ length: 500 }, (_, i) => {
    const y = 1 - (2 * (i + 0.5)) / 500,
      r = Math.sqrt(1 - y * y),
      theta = i * 2.399963;
    return [Math.cos(theta) * r, y, Math.sin(theta) * r];
  });
  function paint(time) {
    const rect = canvas.getBoundingClientRect(),
      ratio = Math.min(devicePixelRatio || 1, 1.5);
    if (
      canvas.width !== Math.round(rect.width * ratio) ||
      canvas.height !== Math.round(rect.height * ratio)
    ) {
      canvas.width = Math.round(rect.width * ratio);
      canvas.height = Math.round(rect.height * ratio);
    }
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, rect.width, rect.height);
    const angle = motionAllowed() ? time * 0.000045 : 0.35;
    const radius = Math.min(rect.width, rect.height) * 0.43,
      cx = rect.width * 0.5,
      cy = rect.height * 0.48;
    for (const [x, y, z] of dots) {
      const xx = x * Math.cos(angle) + z * Math.sin(angle),
        zz = -x * Math.sin(angle) + z * Math.cos(angle),
        scale = 1 + zz * 0.13;
      ctx.fillStyle = `rgba(${zz > 0.3 ? "139,199,223" : "174,138,241"},${0.12 + (zz + 1) * 0.23})`;
      ctx.beginPath();
      ctx.arc(
        cx + xx * radius * scale,
        cy + y * radius * scale,
        zz > 0 ? 1.25 : 0.75,
        0,
        Math.PI * 2,
      );
      ctx.fill();
    }
  }
  function loop(t) {
    paint(t);
    if (visible && !document.hidden && motionAllowed())
      frame = requestAnimationFrame(loop);
    else frame = 0;
  }
  function resume() {
    cancelAnimationFrame(frame);
    frame = 0;
    paint(performance.now());
    if (visible && !document.hidden && motionAllowed())
      frame = requestAnimationFrame(loop);
  }
  const observer = new IntersectionObserver((entries) => {
    visible = entries[0].isIntersecting;
    resume();
  });
  observer.observe(canvas);
  document.addEventListener("visibilitychange", resume);
  media.addEventListener("change", () => {
    applyMotionPreference();
    resume();
  });
  window.addEventListener("resize", resume);
  window.addEventListener("intervie-motion", resume);
  resume();
  window.addEventListener(
    "pagehide",
    () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    },
    { once: true },
  );
}
