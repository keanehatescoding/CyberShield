import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import * as logger from "firebase-functions/logger";
import { assertValidAnswerInput, gradeAnswer, writeGradedResult, finalizeQuizAttempt, AnswerInput } from "./grading";
import { completeModule } from "./modules";

initializeApp();
setGlobalOptions({ region: "me-central1", maxInstances: 20 });

const DEFAULT_DISPLAY_NAME = "CyberShield User";
const MAX_DISPLAY_NAME_LENGTH = 100;

/** Same shape/length bound as firestore.rules enforces for direct client writes to users/{uid}.displayName. */
function sanitizeDisplayName(raw: unknown): string {
  if (typeof raw !== "string") {
    return DEFAULT_DISPLAY_NAME;
  }
  // Trim before slicing, not just for the emptiness check — otherwise a
  // value like "   Ann   " (100 chars including padding) reaches the
  // world-readable leaderboard doc with its whitespace intact, affecting
  // display and sort order in the leaderboard list.
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    return DEFAULT_DISPLAY_NAME;
  }
  return trimmed.slice(0, MAX_DISPLAY_NAME_LENGTH);
}

/**
 * Keeps the public-safe `leaderboard/{uid}` mirror in sync with
 * `users/{uid}`. This used to be a client write (UserRepositoryImpl.
 * createUserProfile / createUserProfileIfNotExists) — moved here so
 * `leaderboard/{userId}` can be locked to `allow write: if false` in
 * firestore.rules with no loss of functionality.
 *
 * - On create (registration or first Google SSO): seeds displayName with
 *   xp/badges at 0/[]; those are only ever incremented afterward by
 *   finalizeQuizAttempt / completeModuleFn.
 * - On update: mirrors a changed displayName only — xp/badges are left
 *   untouched here since this trigger has no knowledge of those awards.
 *   Previously this only ran onDocumentCreated, so a display-name edit
 *   after registration never propagated to the public leaderboard.
 *
 * displayName is sanitized (typeof-checked and length-capped) before being
 * copied into a doc every signed-in user reads via the leaderboard query —
 * the field isn't guarded in firestore.rules' write rule for users/{userId},
 * so a malformed client-written value must not be allowed to corrupt a
 * shared, world-readable document.
 */
export const onUserProfileWritten = onDocumentWritten(
  // retry: true asks Eventarc to redeliver this event on a thrown error
  // (with backoff), instead of a transient Firestore write failure
  // permanently leaving a user missing from / stale on the leaderboard.
  { document: "users/{uid}", region: "me-central1", retry: true },
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    const db = getFirestore();
    const leaderboardRef = db.collection("leaderboard").doc(event.params.uid);

    if (!after || !after.exists) {
      // users/{uid} was deleted — remove the public mirror too, rather than
      // leaving a stale entry (with a now-nonexistent user's last-known
      // name/xp/badges) permanently visible on the leaderboard.
      try {
        await leaderboardRef.delete();
      } catch (e) {
        logger.error("Failed to remove leaderboard entry for deleted profile", { uid: event.params.uid, error: e });
        throw e;
      }
      return;
    }

    const afterData = after.data() as { displayName?: unknown } | undefined;
    const displayName = sanitizeDisplayName(afterData?.displayName);

    try {
      if (!before || !before.exists) {
        // retry: true means Eventarc can redeliver this same create event
        // (with or without an earlier error), so this must not
        // unconditionally re-seed xp/badges — if the redelivery happens
        // after finalizeQuizAttempt / completeModuleFn already incremented
        // the mirror (or a deleted-and-recreated users/{uid} doc lands
        // here after awards existed under the old doc), a bare merge-set of
        // xp: 0, badges: [] would silently wipe them. A transaction that
        // only initializes when the leaderboard doc doesn't yet exist makes
        // repeated/concurrent deliveries safe.
        await db.runTransaction(async (tx) => {
          const snap = await tx.get(leaderboardRef);
          if (!snap.exists) {
            tx.set(leaderboardRef, { displayName, xp: 0, badges: [] });
          } else {
            tx.set(leaderboardRef, { displayName }, { merge: true });
          }
        });
        return;
      }

      const beforeData = before.data() as { displayName?: unknown } | undefined;
      if (sanitizeDisplayName(beforeData?.displayName) !== displayName) {
        await leaderboardRef.set({ displayName }, { merge: true });
      }
    } catch (e) {
      // Logged (not just swallowed) so a persistent failure — e.g. all
      // retries from the `retry: true` policy above exhausted — leaves a
      // trace to back-fill from, instead of the user silently vanishing
      // from (or going stale on) the leaderboard with zero signal.
      logger.error("Failed to mirror displayName to leaderboard", { uid: event.params.uid, error: e });
      throw e;
    }
  },
);

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
    const outcome = await writeGradedResult(
      request.auth.uid,
      graded,
      input.selectedIndex,
      input.answeredAt,
      input.resultId,
      input.timeRemaining,
    );

    // correctIndex is only ever sent back AFTER the client has already
    // submitted its selectedIndex — never available to fetch beforehand.
    // isCorrect reflects what writeGradedResult actually persisted (the
    // first grade recorded for this question/resultId), NOT necessarily
    // this call's fresh grade — see writeGradedResult's first-write-wins
    // doc comment for why re-answering must not flip a stored result.
    return {
      questionId: graded.questionId,
      isCorrect: outcome.isCorrect,
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
      try {
        assertValidAnswerInput(raw);
        const input = raw as AnswerInput;
        const graded = await gradeAnswer(input);
        const outcome = await writeGradedResult(
          request.auth.uid,
          graded,
          input.selectedIndex,
          input.answeredAt,
          input.resultId,
          input.timeRemaining,
        );
        results.push({
          questionId: graded.questionId,
          isCorrect: outcome.isCorrect,
          correctIndex: graded.correctIndex,
          explanation: graded.explanation,
          error: null,
        });
      } catch (e) {
        // One bad row (e.g. a question that was later deleted, or a
        // malformed cached entry) shouldn't fail the whole batch — report
        // it and let the rest sync. assertValidAnswerInput is inside this
        // try too, so a row that fails shape validation is reported the
        // same way instead of throwing out of the whole callable.
        const questionId = (raw as Partial<AnswerInput> | null)?.questionId ?? null;
        results.push({
          questionId,
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

/**
 * Marks a module complete and awards its xpReward — see completeModule()
 * in modules.ts for why this moved server-side.
 */
export const completeModuleFn = onCall(
  {
    enforceAppCheck: true,
    consumeAppCheckToken: true,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const data = request.data as { moduleId?: unknown; watchedMs?: unknown } | null;
    const moduleId = data?.moduleId;
    if (typeof moduleId !== "string" || !moduleId) {
      throw new HttpsError("invalid-argument", "moduleId is required.");
    }
    const watchedMs = typeof data?.watchedMs === "number" ? data.watchedMs : -1;
    return completeModule(request.auth.uid, moduleId, watchedMs);
  },
);
