import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

/**
 * The answer key lives in a collection the client can never read
 * (see firestore.rules: `allow read, write: if false;`). It is only ever
 * touched by the Admin SDK from these Cloud Functions.
 *
 * Document id: `${quizId}_${questionId}`
 */
export interface AnswerKeyDoc {
  correctIndex: number;
  optionCount: number;
  explanation?: string;
  moduleId?: string;
}

export interface AnswerInput {
  quizId: string;
  questionId: string;
  selectedIndex: number;
  /** Groups every answer from a single quiz attempt so the server can recompute and issue the certificate. */
  resultId: string;
  /** Client's leftover time (seconds) when the answer was submitted; feeds the speed-weighted certificate score. */
  timeRemaining?: number;
  /** Client-reported answer timestamp, used only for display/history ordering — never trusted for scoring. */
  answeredAt?: number;
}

export interface GradedAnswer {
  quizId: string;
  questionId: string;
  isCorrect: boolean;
  correctIndex: number;
  explanation: string;
  moduleId: string;
}

/** Basic shape validation so a malformed payload fails fast with a clear error. */
export function assertValidAnswerInput(input: unknown): asserts input is AnswerInput {
  const a = input as Partial<AnswerInput> | null;
  if (
    !a ||
    typeof a.quizId !== "string" ||
    !a.quizId ||
    typeof a.questionId !== "string" ||
    !a.questionId ||
    typeof a.selectedIndex !== "number" ||
    !Number.isInteger(a.selectedIndex) ||
    typeof a.resultId !== "string" ||
    !a.resultId
  ) {
    throw new HttpsError("invalid-argument", "quizId, questionId, an integer selectedIndex, and resultId are required.");
  }
}

/**
 * Looks up the answer key and grades a single answer. Throws HttpsError if
 * the question doesn't exist — this deliberately does NOT leak whether the
 * quizId or questionId was wrong vs. something else, to avoid giving a
 * scripted client a way to enumerate valid ids.
 */
export async function gradeAnswer(input: AnswerInput): Promise<GradedAnswer> {
  const db = getFirestore();
  const keyRef = db.collection("answerKeys").doc(`${input.quizId}_${input.questionId}`);
  const keySnap = await keyRef.get();

  if (!keySnap.exists) {
    throw new HttpsError("not-found", "Question not found.");
  }

  const key = keySnap.data() as AnswerKeyDoc;

  if (input.selectedIndex < -1 || input.selectedIndex >= key.optionCount) {
    // -1 is the sentinel the client sends for "timed out, no answer".
    throw new HttpsError("invalid-argument", "selectedIndex is out of range for this question.");
  }

  return {
    quizId: input.quizId,
    questionId: input.questionId,
    isCorrect: input.selectedIndex === key.correctIndex,
    correctIndex: key.correctIndex,
    explanation: key.explanation ?? "",
    moduleId: key.moduleId ?? "",
  };
}

/**
 * Persists a graded answer to `users/{uid}/quizResults/{quizId}_{questionId}`.
 * This is the ONLY code path allowed to write `isCorrect` — Firestore rules
 * block client writes to this subcollection entirely (see firestore.rules).
 */
export async function writeGradedResult(
  uid: string,
  graded: GradedAnswer,
  selectedIndex: number,
  clientAnsweredAt: number | undefined,
  resultId: string,
  timeRemaining?: number,
): Promise<void> {
  const db = getFirestore();
  const ref = db
    .collection("users")
    .doc(uid)
    .collection("quizResults")
    .doc(`${graded.quizId}_${graded.questionId}`);

  await ref.set(
    {
      quizId: graded.quizId,
      questionId: graded.questionId,
      moduleId: graded.moduleId,
      resultId: resultId ?? "",
      selectedIndex,
      isCorrect: graded.isCorrect,
      timeRemaining: typeof timeRemaining === "number" ? timeRemaining : null,
      clientAnsweredAt: clientAnsweredAt ?? null,
      validatedAt: FieldValue.serverTimestamp(),
      validatedBy: "cloudFunction",
    },
    { merge: true },
  );
}

export const PASS_PERCENTAGE = 70;

// Mirrors QuizViewModel.QUESTION_TIME_SECONDS on the client — the countdown
// starts at this value, so a legitimate timeRemaining can never exceed it.
// timeRemaining is client-supplied (see AnswerInput) and only ever feeds the
// speed component of the certificate/history score, never XP/percentage/pass,
// so clamping it here is enough to stop a client inflating that score by
// sending an arbitrarily large value.
export const MAX_TIME_REMAINING_SECONDS = 30;

export function clampTimeRemaining(timeRemaining: unknown): number {
  if (typeof timeRemaining !== "number" || !Number.isFinite(timeRemaining)) {
    return 0;
  }
  return Math.min(Math.max(timeRemaining, 0), MAX_TIME_REMAINING_SECONDS);
}

// XP formula — this used to live client-side (AwardXpUseCase) and be
// applied via a direct client Firestore write to users/{uid}.xp and
// leaderboard/{uid}.xp. That made XP (and therefore leaderboard rank)
// trivially forgeable by any authenticated client, since the write rule
// only checked auth.uid == userId with no validation of the increment
// amount. XP is now computed and applied ONLY here, with the Admin SDK.
export const XP_PER_CORRECT_ANSWER = 10;
export const XP_BONUS_PERFECT_SCORE = 50;

export interface FinalizeQuizAttemptResult {
  passed: boolean;
  score: number;
  correctCount: number;
  percentage: number;
  xpEarned: number;
  alreadyFinalized?: boolean;
}

/**
 * Recomputes a quiz attempt's score entirely from the server-graded
 * `quizResults` (isCorrect is written by writeGradedResult, never by the
 * client), awards XP, and — if the attempt passed — issues the certificate
 * and the CyberDefender badge. This is the ONLY path allowed to create a
 * certificate or award XP, so neither can be forged by a client.
 *
 * Idempotent: a `quizAttempts/{resultId}` marker doc records the outcome the
 * first time this runs; retries (e.g. from the offline-sync path) read that
 * marker back instead of re-incrementing XP or re-issuing the cert/badge.
 */
export async function finalizeQuizAttempt(uid: string, resultId: string): Promise<FinalizeQuizAttemptResult> {
  const db = getFirestore();
  if (!resultId || typeof resultId !== "string") {
    throw new HttpsError("invalid-argument", "resultId is required.");
  }

  const userRef = db.collection("users").doc(uid);
  const attemptRef = userRef.collection("quizAttempts").doc(resultId);

  const existingAttempt = await attemptRef.get();
  if (existingAttempt.exists) {
    const data = existingAttempt.data() as FinalizeQuizAttemptResult;
    return { ...data, alreadyFinalized: true };
  }

  const resultsSnap = await userRef.collection("quizResults").where("resultId", "==", resultId).get();

  if (resultsSnap.empty) {
    throw new HttpsError("not-found", "No graded answers found for this attempt.");
  }

  const results = resultsSnap.docs.map((d) => d.data() as Record<string, unknown>);
  const total = results.length;
  const correctResults = results.filter((r) => r.isCorrect === true);
  const correctCount = correctResults.length;
  const percentage = total > 0 ? Math.round((correctCount * 100) / total) : 0;
  const passed = percentage >= PASS_PERCENTAGE;

  // speed-weighted score — mirrors the client's GenerateCertificateUseCase.
  // timeRemaining is client-supplied and stored as-is by writeGradedResult,
  // so it's clamped here to the max a legitimate countdown could produce
  // before it's used to compute a score that ends up on the certificate.
  const score = correctResults.reduce((sum, r) => sum + (100 + clampTimeRemaining(r.timeRemaining) * 5), 0);

  const xpEarned = correctCount * XP_PER_CORRECT_ANSWER + (total > 0 && correctCount === total ? XP_BONUS_PERFECT_SCORE : 0);

  const leaderboardRef = db.collection("leaderboard").doc(uid);
  const batch = db.batch();

  batch.update(userRef, { xp: FieldValue.increment(xpEarned) });
  batch.set(leaderboardRef, { xp: FieldValue.increment(xpEarned) }, { merge: true });

  let finalScore = score;
  if (passed) {
    const first = results[0] as { moduleId?: string; quizId?: string };
    const moduleId = first.moduleId ?? "";
    const quizId = first.quizId ?? "";

    const [userSnap, moduleSnap, quizSnap] = await Promise.all([
      userRef.get(),
      moduleId ? db.collection("modules").doc(moduleId).get() : Promise.resolve(null),
      quizId ? db.collection("quizzes").doc(quizId).get() : Promise.resolve(null),
    ]);

    const userData = (userSnap.data() ?? {}) as { displayName?: string };
    const displayName = userData.displayName ?? "CyberShield User";
    const moduleName = moduleSnap && moduleSnap.exists ? ((moduleSnap.data() as { title?: string }).title ?? "") : "";
    const quizTitle =
      quizSnap && quizSnap.exists ? ((quizSnap.data() as { title?: string }).title ?? "CyberShield Quiz") : "CyberShield Quiz";

    const certRef = userRef.collection("certificates").doc(resultId);
    batch.set(certRef, {
      id: resultId,
      userId: uid,
      userName: displayName,
      moduleId,
      moduleName,
      quizTitle,
      score,
      issuedAt: FieldValue.serverTimestamp(),
    });

    batch.update(userRef, { badges: FieldValue.arrayUnion("CyberDefender") });
    batch.set(leaderboardRef, { badges: FieldValue.arrayUnion("CyberDefender") }, { merge: true });
  } else {
    finalScore = 0;
  }

  const attemptResult: FinalizeQuizAttemptResult = {
    passed,
    score: finalScore,
    correctCount,
    percentage,
    xpEarned,
  };
  batch.set(attemptRef, {
    ...attemptResult,
    finalizedAt: FieldValue.serverTimestamp(),
  });

  await batch.commit();

  return attemptResult;
}
