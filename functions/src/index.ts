import { initializeApp } from "firebase-admin/app";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import { assertValidAnswerInput, gradeAnswer, writeGradedResult, finalizeQuizAttempt, AnswerInput } from "./grading";

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 20 });

/**
 * Called immediately when the device is online. Grades one answer against
 * the server-only answer key and records the validated result. The client
 * never sends `isCorrect` — it only ever sends `selectedIndex`, and only
 * ever receives `isCorrect` back, never `correctIndex`.
 */
export const validateAnswer = onCall(
  {
    // Require App Check so only the real CyberShield app (not a scripted
    // client hitting the callable URL directly) can call this.
    enforceAppCheck: true,
    consumeAppCheckToken: true,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    assertValidAnswerInput(request.data);
    const input = request.data as AnswerInput;

    const graded = await gradeAnswer(input);
    await writeGradedResult(request.auth.uid, graded, input.selectedIndex, input.answeredAt, input.resultId, input.timeRemaining);

    // correctIndex is only ever sent back AFTER the client has already
    // submitted its selectedIndex — never available to fetch beforehand.
    return {
      questionId: graded.questionId,
      isCorrect: graded.isCorrect,
      correctIndex: graded.correctIndex,
      explanation: graded.explanation,
    };
  },
);

/**
 * Called by SyncQuizResultsWorker once connectivity returns, for answers
 * that were given while offline (cached locally with no isCorrect yet).
 * Grades and persists each one, then returns per-question results so the
 * app can update its local Room cache and reveal the deferred feedback.
 */
export const validateAnswersBatch = onCall(
  {
    enforceAppCheck: true,
    consumeAppCheckToken: true,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const answers = (request.data as { answers?: unknown[] } | null)?.answers;
    if (!Array.isArray(answers) || answers.length === 0) {
      throw new HttpsError("invalid-argument", "answers must be a non-empty array.");
    }
    if (answers.length > 100) {
      throw new HttpsError("invalid-argument", "Batch too large — send at most 100 answers per call.");
    }

    const results = [];
    for (const raw of answers) {
      assertValidAnswerInput(raw);
      const input = raw as AnswerInput;
      try {
        const graded = await gradeAnswer(input);
        await writeGradedResult(request.auth.uid, graded, input.selectedIndex, input.answeredAt, input.resultId, input.timeRemaining);
        results.push({
          questionId: graded.questionId,
          isCorrect: graded.isCorrect,
          correctIndex: graded.correctIndex,
          explanation: graded.explanation,
          error: null,
        });
      } catch (e) {
        // One bad row (e.g. a question that was later deleted) shouldn't
        // fail the whole batch — report it and let the rest sync.
        results.push({
          questionId: input.questionId,
          isCorrect: null,
          correctIndex: null,
          explanation: null,
          error: e instanceof HttpsError ? e.message : "Failed to validate.",
        });
      }
    }

    return { results };
  },
);

/**
 * Issues the certificate + CyberDefender badge for a quiz attempt, computed
 * entirely from the server-graded `quizResults`. The client never writes
 * certificates or the badge — this is the only writer, so certificates
 * cannot be forged. Idempotent (see finalizeQuizAttempt).
 */
export const finalizeQuizAttemptFn = onCall(
  {
    enforceAppCheck: true,
    consumeAppCheckToken: true,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const resultId = (request.data as { resultId?: unknown } | null)?.resultId;
    if (typeof resultId !== "string" || !resultId) {
      throw new HttpsError("invalid-argument", "resultId is required.");
    }
    return finalizeQuizAttempt(request.auth.uid, resultId);
  },
);
