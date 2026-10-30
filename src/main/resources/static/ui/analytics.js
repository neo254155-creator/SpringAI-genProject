import { summarizeProgress } from "../history-client.js";
export function buildAnalytics(progress, sessions, resumes, now = new Date()) {
  const totals = summarizeProgress(progress);
  const points = resumes
    .filter(
      (r) =>
        r?.answer?.feedback &&
        Number.isFinite(r.answer.feedback.score) &&
        r.answer.submittedAt,
    )
    .map((r) => ({
      score: r.answer.feedback.score,
      time: r.answer.submittedAt,
      label: `${r.question?.skill || r.session.topic} · ${new Date(r.answer.submittedAt).toLocaleDateString()}`,
    }))
    .sort((a, b) => new Date(a.time) - new Date(b.time));
  const topics = new Map();
  for (const row of progress) {
    const old = topics.get(row.topic) || {
      topic: row.topic,
      total: 0,
      attempts: 0,
    };
    old.total += row.averageScore * row.attempts;
    old.attempts += row.attempts;
    topics.set(row.topic, old);
  }
  const skills = [...topics.values()].map((t) => ({
    ...t,
    score: t.total / t.attempts,
  }));
  const recommendations = [...progress]
    .sort((a, b) => a.averageScore - b.averageScore)
    .slice(0, 3);
  const days = Array.from({ length: 7 }, (_, i) => {
    const date = new Date(now);
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - 6 + i);
    const next = new Date(date);
    next.setDate(next.getDate() + 1);
    return {
      date,
      count: sessions.filter(
        (s) => new Date(s.createdAt) >= date && new Date(s.createdAt) < next,
      ).length,
    };
  });
  return {
    totals,
    points,
    skills,
    recommendations,
    days,
    trend: points.length >= 2 ? points.at(-1).score - points[0].score : null,
  };
}
