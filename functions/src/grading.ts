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

/**
 * Recomputes a quiz attempt's score entirely from the server-graded
 * `quizResults` (isCorrect is written by writeGradedResult, never by the
 * client) and, if the attempt passed, issues the certificate and the
 * CyberDefender badge. This is the ONLY path allowed to create a certificate,
 * so a client can never forge a passing certificate.
 *
 * Idempotent: the certificate document id equals `resultId` and the badge is
 * added via arrayUnion, so retrying never double-issues or double-counts.
 */
export async function finalizeQuizAttempt(
  uid: string,
  resultId: string,
): Promise<{ passed: boolean; score: number; correctCount: number; percentage: number; alreadyFinalized?: boolean }> {
  const db = getFirestore();
  if (!resultId || typeof resultId !== "string") {
    throw new HttpsError("invalid-argument", "resultId is required.");
  }

  const resultsSnap = await db
    .collection("users")
    .doc(uid)
    .collection("quizResults")
    .where("resultId", "==", resultId)
    .get();

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
  const score = correctResults.reduce(
    (sum, r) => sum + (100 + (typeof r.timeRemaining === "number" ? (r.timeRemaining as number) : 0) * 5),
    0,
  );

  if (!passed) {
    return { passed: false, score: 0, correctCount, percentage };
  }

  const certRef = db.collection("users").doc(uid).collection("certificates").doc(resultId);
  const existing = await certRef.get();
  if (existing.exists) {
    const data = existing.data() as { score?: number } | undefined;
    return {
      passed: true,
      score: data?.score ?? score,
      correctCount,
      percentage,
      alreadyFinalized: true,
    };
  }

  const first = results[0] as { moduleId?: string; quizId?: string };
  const moduleId = first.moduleId ?? "";
  const quizId = first.quizId ?? "";

  const [userSnap, moduleSnap, quizSnap] = await Promise.all([
    db.collection("users").doc(uid).get(),
    moduleId ? db.collection("modules").doc(moduleId).get() : Promise.resolve(null),
    quizId ? db.collection("quizzes").doc(quizId).get() : Promise.resolve(null),
  ]);

  const userData = (userSnap.data() ?? {}) as { displayName?: string };
  const displayName = userData.displayName ?? "CyberShield User";
  const moduleName = moduleSnap && moduleSnap.exists ? ((moduleSnap.data() as { title?: string }).title ?? "") : "";
  const quizTitle = quizSnap && quizSnap.exists ? ((quizSnap.data() as { title?: string }).title ?? "CyberShield Quiz") : "CyberShield Quiz";

  await certRef.set({
    id: resultId,
    userId: uid,
    userName: displayName,
    moduleId,
    moduleName,
    quizTitle,
    score,
    issuedAt: FieldValue.serverTimestamp(),
  });

  await db
    .collection("users")
    .doc(uid)
    .update({ badges: FieldValue.arrayUnion("CyberDefender") });

  return { passed: true, score, correctCount, percentage };
}
