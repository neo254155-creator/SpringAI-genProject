import { apiUrl } from "./ui/api-config.js";

/** Session workflow shared by the browser UI and offline workflow tests. */
function validFeedback(feedback) {
  return Boolean(
    feedback &&
    Number.isInteger(feedback.score) &&
    feedback.score >= 0 &&
    feedback.score <= 10 &&
    feedback.maxScore === 10 &&
    typeof feedback.explanation === "string" &&
    Array.isArray(feedback.correctPoints) &&
    feedback.correctPoints.every((p) => typeof p === "string") &&
    Array.isArray(feedback.missingPoints) &&
    feedback.missingPoints.every((p) => typeof p === "string") &&
    [
      ["correctness", 5],
      ["completeness", 3],
      ["clarity", 2],
    ].every(
      ([key, max]) =>
        Number.isInteger(feedback.rubric?.[key]?.score) &&
        feedback.rubric[key].score >= 0 &&
        feedback.rubric[key].score <= max &&
        typeof feedback.rubric[key].reason === "string",
    ),
  );
}

export async function requestJson(
  path,
  { method = "GET", body, signal, fetcher = globalThis.fetch } = {},
) {
  const response = await fetcher(apiUrl(path), {
    method,
    signal,
    headers: {
      Accept: "application/json",
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  return readJsonResponse(response);
}

export async function readJsonResponse(response) {
  let data;
  try {
    data = await response.json();
  } catch {
    throw new Error(
      `The server returned an unreadable response (${response.status}).`,
    );
  }
  if (!response.ok)
    throw new Error(data?.detail || "The request failed. Please try again.");
  return data;
}

export class InterviewFlow {
  session = null;
  question = null;
  feedback = null;
  savedAnswer = "";
  busy = false;
  answered = 0;

  constructor(fetcher = globalThis.fetch) {
    this.fetcher = fetcher;
  }

  async #request(path, method, body, signal) {
    return requestJson(path, { method, body, signal, fetcher: this.fetcher });
  }

  async #run(action) {
    if (this.busy) throw new Error("Please wait for the current request.");
    this.busy = true;
    try {
      return await action();
    } finally {
      this.busy = false;
    }
  }

  start(selection, signal) {
    return this.#startAt("/interview/sessions", selection, signal);
  }

  startFromResume(id, selection, signal, onCreated) {
    return this.#startAt(
      `/resumes/${encodeURIComponent(id)}/sessions`,
      selection,
      signal,
      onCreated,
    );
  }

  #startAt(path, selection, signal, onCreated) {
    return this.#run(async () => {
      if (this.session)
        throw new Error(
          "Finish the current interview before starting another.",
        );
      const session = await this.#request(path, "POST", selection, signal);
      if (!session?.id || session.status !== "ACTIVE")
        throw new Error("The server did not return an active session.");
      this.session = session;
      onCreated?.(session);
      // Retain the session if question generation fails; retry must not create another.
      await this.#generate(signal);
    });
  }

  resume(id, signal) {
    return this.#run(async () => {
      if (this.session)
        throw new Error("An interview is already open in this screen.");
      const result = await this.#request(
        `/interview/sessions/${encodeURIComponent(id)}/resume`,
        "GET",
        undefined,
        signal,
      );
      if (
        !result?.session ||
        result.session.id !== id ||
        !["ACTIVE", "COMPLETED"].includes(result.session.status) ||
        !Number.isSafeInteger(result.questionsAnswered) ||
        result.questionsAnswered < 0 ||
        (result.question &&
          (!result.question.id ||
            result.question.sessionId !== id ||
            typeof result.question.question !== "string")) ||
        (result.answer &&
          (!result.question ||
            result.answer.questionId !== result.question.id ||
            typeof result.answer.answer !== "string" ||
            !validFeedback(result.answer.feedback)))
      ) {
        throw new Error("The server did not return a valid saved session.");
      }
      this.session = result.session;
      this.question = result.question;
      this.feedback = result.answer?.feedback ?? null;
      this.savedAnswer = result.answer?.answer ?? "";
      this.answered = result.questionsAnswered;
    });
  }

  nextQuestion(signal) {
    return this.#run(async () => {
      this.#requireActive();
      if (this.question && !this.feedback)
        throw new Error(
          "Submit your answer before requesting the next question.",
        );
      await this.#generate(signal);
    });
  }

  async #generate(signal) {
    const question = await this.#request(
      `/interview/sessions/${this.session.id}/questions`,
      "POST",
      undefined,
      signal,
    );
    if (
      !question?.id ||
      question.sessionId !== this.session.id ||
      typeof question.question !== "string" ||
      !question.question.trim()
    ) {
      throw new Error("The server did not return a valid question.");
    }
    this.question = question;
    this.feedback = null;
    this.savedAnswer = "";
  }

  submit(answer, signal) {
    return this.#run(async () => {
      this.#requireActive();
      if (!this.question || this.feedback)
        throw new Error("There is no unanswered question.");
      if (
        typeof answer !== "string" ||
        !answer.trim() ||
        answer.length > 8000
      ) {
        throw new Error("Enter an answer between 1 and 8,000 characters.");
      }
      const result = await this.#request(
        `/interview/questions/${this.question.id}/answers`,
        "POST",
        { answer },
        signal,
      );
      const feedback = result?.feedback;
      if (result?.questionId !== this.question.id || !validFeedback(feedback)) {
        throw new Error("The server did not return valid feedback.");
      }
      this.feedback = feedback;
      this.savedAnswer = answer;
      this.answered++;
    });
  }

  complete(signal) {
    return this.#run(async () => {
      this.#requireActive();
      const result = await this.#request(
        `/interview/sessions/${this.session.id}/complete`,
        "POST",
        undefined,
        signal,
      );
      if (result?.id !== this.session.id || result.status !== "COMPLETED")
        throw new Error("Could not confirm session completion.");
      this.session = result;
    });
  }

  reset() {
    if (this.busy || (this.session && this.session.status !== "COMPLETED"))
      throw new Error("Finish the current interview first.");
    this.session = this.question = this.feedback = null;
    this.savedAnswer = "";
    this.answered = 0;
  }

  #requireActive() {
    if (this.session?.status !== "ACTIVE")
      throw new Error("Start an interview first.");
  }
}
