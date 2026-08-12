import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { isValidFirestoreDocId } from "./validation";

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
    typeof a.selectedIndex !== "number" ||
    !Number.isInteger(a.selectedIndex)
  ) {
    throw new HttpsError("invalid-argument", "quizId, questionId, an integer selectedIndex, and resultId are required.");
  }

  // quizId and questionId are combined into the answerKeys/quizResults doc
  // id (`${quizId}_${questionId}`) and resultId is used directly as the
  // quizAttempts/certificates doc id, so all three must be safe Firestore
  // doc-id segments — otherwise a malformed value (e.g. containing "/")
  // throws a raw SDK error deep inside gradeAnswer/writeGradedResult
  // instead of a clean invalid-argument here.
  if (!isValidFirestoreDocId(a.quizId)) {
    throw new HttpsError("invalid-argument", "quizId must be a non-empty, valid document id.");
  }
  if (!isValidFirestoreDocId(a.questionId)) {
    throw new HttpsError("invalid-argument", "questionId must be a non-empty, valid document id.");
  }
  if (!isValidFirestoreDocId(a.resultId)) {
    throw new HttpsError("invalid-argument", "resultId must be a non-empty, valid document id.");
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

export interface WriteGradedResultOutcome {
  /** The isCorrect value actually persisted for this attempt/question — see below. */
  isCorrect: boolean;
  /** True when this question already had a graded result for this resultId. */
  alreadyAnswered: boolean;
}

/** `users/{uid}/quizFirstAttempts/{quizId}` doc shape — see writeGradedResult. */
interface FirstAttemptDoc {
  resultId: string;
}

/**
 * Persists a graded answer to `users/{uid}/quizResults/{resultId}_{questionId}`.
 * This is the ONLY code path allowed to write `isCorrect` — Firestore rules
 * block client writes to this subcollection entirely (see firestore.rules).
 *
 * Keyed by resultId (not quizId) and first-write-wins: validateAnswer always
 * reveals correctIndex in its response so the UI can show the right answer
 * after submitting (see index.ts). Without pinning the doc id to resultId and
 * refusing to overwrite an existing grade, a client could submit a throwaway
 * guess for a question to read back correctIndex, then call validateAnswer
 * again for the same question under the same resultId with the now-known
 * correct index, overwriting isCorrect: false with isCorrect: true before
 * finalizeQuizAttempt ever runs. Keying by resultId also stops two concurrent
 * attempts of the same quiz (e.g. a retake started while an earlier attempt's
 * offline-sync batch is still landing) from overwriting each other's answers,
 * since they no longer share a doc id.
 *
 * That alone is NOT sufficient, though: resultId is entirely client-chosen,
 * so a client could still submit a throwaway guess under resultId A (reading
 * back correctIndex), then submit the now-known correct answer under a
 * brand-new resultId B, and finalize B instead — laundering the leaked
 * answer into a fresh, unpolluted attempt. To close that, this also claims
 * `quizFirstAttempts/{quizId}` the first time this user ever gets a graded
 * answer for a given quiz: whichever resultId gets there first is the only
 * one finalizeQuizAttempt will ever award XP for (see there). The claim is
 * set-if-absent and happens here — at grading/reveal time, not at finalize
 * time — because the exploit hinges on laundering a reveal that already
 * happened; claiming only at finalize would be too late (A, the peeked
 * attempt, is never finalized, so nothing would ever bind quizId to A).
 */
export async function writeGradedResult(
  uid: string,
  graded: GradedAnswer,
  selectedIndex: number,
  clientAnsweredAt: number | undefined,
  resultId: string,
  timeRemaining?: number,
): Promise<WriteGradedResultOutcome> {
  const db = getFirestore();
  const ref = db
    .collection("users")
    .doc(uid)
    .collection("quizResults")
    .doc(`${resultId}_${graded.questionId}`);
  const firstAttemptRef = graded.quizId
    ? db.collection("users").doc(uid).collection("quizFirstAttempts").doc(graded.quizId)
    : null;

  return db.runTransaction(async (tx) => {
    const [existing, firstAttemptSnap] = await Promise.all([
      tx.get(ref),
      firstAttemptRef ? tx.get(firstAttemptRef) : Promise.resolve(null),
    ]);
    if (existing.exists) {
      // Idempotent for legitimate retries (e.g. offline-sync re-sending an
      // answer whose ack was lost) and immune to the peek-then-correct
      // exploit: whatever was recorded first stands, regardless of what the
      // current call's selectedIndex claims.
      const data = existing.data() as { isCorrect: boolean };
      return { isCorrect: data.isCorrect, alreadyAnswered: true };
    }

    if (firstAttemptRef && firstAttemptSnap && !firstAttemptSnap.exists) {
      const claim: FirstAttemptDoc = { resultId };
      tx.set(firstAttemptRef, claim);
    }

    tx.set(ref, {
      quizId: graded.quizId,
      questionId: graded.questionId,
      moduleId: graded.moduleId,
      resultId,
      selectedIndex,
      isCorrect: graded.isCorrect,
      timeRemaining: typeof timeRemaining === "number" ? timeRemaining : null,
      clientAnsweredAt: clientAnsweredAt ?? null,
      validatedAt: FieldValue.serverTimestamp(),
      validatedBy: "cloudFunction",
    });

    return { isCorrect: graded.isCorrect, alreadyAnswered: false };
  });
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
  /**
   * True when this is a retake — the user already had a prior finalized
   * attempt for this quizId (identified by completedQuizzes containing it),
   * so no XP is awarded. The client uses this to adjust result-screen copy
   * ("Practice complete" vs "Quiz complete — you earned N XP").
   */
  alreadyAttempted?: boolean;
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
 *
 * Retake policy: XP is only awarded on the very first finalized attempt for
 * each quizId (pass or fail). Subsequent attempts still score and issue a
 * certificate if passed, but earn 0 XP. This is enforced server-side by
 * checking completedQuizzes before the increment — a client cannot influence
 * this check because completedQuizzes is written only by this function and
 * is blocked from client writes in firestore.rules.
 *
 * The idempotency check and every write below run inside a single Firestore
 * transaction. Firebase Functions can (and does) receive two concurrent
 * calls for the same resultId — e.g. the offline-sync retry firing while an
 * earlier attempt is still in flight. With a plain get()-then-batch, both
 * calls can read `alreadyFinalized: false` before either commits, and both
 * go on to award XP and issue a certificate/badge a second time. A
 * transaction makes Firestore retry one of the two callers if their read
 * sets overlap, so only one ever observes the "not yet finalized" state and
 * proceeds to write.
 */
export async function finalizeQuizAttempt(uid: string, resultId: string): Promise<FinalizeQuizAttemptResult> {
  const db = getFirestore();
  if (!isValidFirestoreDocId(resultId)) {
    throw new HttpsError("invalid-argument", "resultId must be a non-empty, valid document id.");
  }

  const userRef = db.collection("users").doc(uid);
  const attemptRef = userRef.collection("quizAttempts").doc(resultId);
  const leaderboardRef = db.collection("leaderboard").doc(uid);

  return db.runTransaction(async (tx) => {
    // All reads must happen before any writes within a Firestore
    // transaction, so every get()/query this function needs is gathered
    // up front via tx.get(), and the batch.* calls below become tx.* calls.
    // NOTE: quizId isn't known yet at this point (it's read from quizResults
    // below), so the quizFirstAttempts lookup happens further down, once
    // quizId is available — still before any writes.
    const existingAttempt = await tx.get(attemptRef);
    if (existingAttempt.exists) {
      const data = existingAttempt.data() as FinalizeQuizAttemptResult;
      return { ...data, alreadyFinalized: true };
    }

    const resultsSnap = await tx.get(userRef.collection("quizResults").where("resultId", "==", resultId));

    if (resultsSnap.empty) {
      throw new HttpsError("not-found", "No graded answers found for this attempt.");
    }

    const allResults = resultsSnap.docs.map((d) => d.data() as Record<string, unknown>);

    const first = allResults[0] as { moduleId?: string; quizId?: string };
    const quizId = first.quizId ?? "";
    const moduleId = first.moduleId ?? "";

    if (!quizId) {
      throw new HttpsError("failed-precondition", "Attempt has no associated quiz.");
    }

    // See writeGradedResult's doc comment: the first resultId that ever got
    // a graded answer for this quiz is the only one eligible for XP. Without
    // this, first-write-wins (which only locks a single resultId) wouldn't
    // stop a client from peeking at correctIndex under a throwaway resultId,
    // then submitting correct answers under a brand-new resultId and
    // finalizing that "clean" one instead. A quizFirstAttempts doc always
    // exists by the time finalize is reachable here — finalizing requires
    // every question to already have a graded quizResults doc (checked
    // below), and grading a question is exactly what claims this doc.
    const firstAttemptSnap = await tx.get(userRef.collection("quizFirstAttempts").doc(quizId));
    const firstAttemptResultId = (firstAttemptSnap.data() as { resultId?: string } | undefined)?.resultId;
    const isLaunderedResultId = firstAttemptResultId !== undefined && firstAttemptResultId !== resultId;

    // resultId is client-supplied and validateAnswer will grade *any*
    // question under *any* resultId, so a client could otherwise submit
    // extra graded answers from unrelated quizzes/questions tagged with
    // this same resultId to pad total/correctCount. Only count results that
    // actually belong to this attempt's quiz.
    const results = allResults.filter((r) => r.quizId === quizId);
    const total = results.length;
    const correctResults = results.filter((r) => r.isCorrect === true);
    const correctCount = correctResults.length;

    // Cross-check against the quiz's real question count before trusting
    // `total` as the denominator. This must be an EXACT match, not just a
    // minimum: a client could call validateAnswer for one easy question,
    // then invoke this callable directly with that resultId (total = 1,
    // instant pass) — or, without the quizId filter above, brute-force and
    // submit answers to every question in the app under one resultId so
    // `total` sails past `expectedQuestionCount` and the old `total <
    // expectedQuestionCount` check never fires, inflating xpEarned with no
    // cap. Requiring `total === expectedQuestionCount` closes both.
    const questionsCountSnap = await tx.get(db.collection("quizzes").doc(quizId).collection("questions").count());
    const expectedQuestionCount = questionsCountSnap.data().count ?? 0;

    if (expectedQuestionCount === 0 || total !== expectedQuestionCount) {
      throw new HttpsError(
        "failed-precondition",
        `Attempt is incomplete: ${total}/${expectedQuestionCount} questions graded.`,
      );
    }

    // Read the user doc now (before any writes). We need it for two things:
    //   1. The retake check — has this quizId already appeared in completedQuizzes?
    //   2. displayName for the certificate (only if passing).
    // We always read it unconditionally so the retake check never races with
    // a concurrent first attempt — if two first-attempt calls land in parallel
    // and both read completedQuizzes before either commits, Firestore will
    // retry one of them (their read sets overlap on userRef), so only one
    // ever sees completedQuizzes without this quizId and earns XP.
    const userSnap = await tx.get(userRef);
    const userData = (userSnap.data() ?? {}) as { completedQuizzes?: string[]; displayName?: string };

    // XP is awarded only on the first ever finalized attempt for this quiz
    // (pass or fail). `completedQuizzes` is written here and is blocked from
    // any client write in firestore.rules, so this cannot be bypassed.
    // isLaunderedResultId (see above) closes the remaining gap: even a
    // resultId that's genuinely this user's first *finalized* attempt
    // (completedQuizzes doesn't have quizId yet) is only genuinely their
    // first *attempt* — and thus XP-eligible — if it's also the resultId
    // that was first ever graded for this quiz.
    const alreadyAttempted = (userData.completedQuizzes ?? []).includes(quizId) || isLaunderedResultId;

    const percentage = total > 0 ? Math.round((correctCount * 100) / total) : 0;
    const passed = percentage >= PASS_PERCENTAGE;

    // speed-weighted score — mirrors the client's QuizScoring formula.
    // timeRemaining is client-supplied and stored as-is by writeGradedResult,
    // so it's clamped here to the max a legitimate countdown could produce
    // before it's used to compute a score that ends up on the certificate.
    const score = correctResults.reduce((sum, r) => sum + (100 + clampTimeRemaining(r.timeRemaining) * 5), 0);

    const xpEarned = alreadyAttempted
      ? 0
      : correctCount * XP_PER_CORRECT_ANSWER + (total > 0 && correctCount === total ? XP_BONUS_PERFECT_SCORE : 0);

    // Any further reads for the certificate (module/quiz title) must also
    // happen before the writes below.
    const [moduleSnap, quizSnap] = passed
      ? await Promise.all([
          moduleId ? tx.get(db.collection("modules").doc(moduleId)) : Promise.resolve(null),
          quizId ? tx.get(db.collection("quizzes").doc(quizId)) : Promise.resolve(null),
        ])
      : [null, null];

    // --- writes from here on ---

    if (xpEarned > 0) {
      tx.update(userRef, { xp: FieldValue.increment(xpEarned) });
      tx.set(leaderboardRef, { xp: FieldValue.increment(xpEarned) }, { merge: true });
    }

    // completedQuizzes is server-only now, for the same reason completedModules
    // is: it used to be a direct client write (UserRepositoryImpl.markQuizCompleted,
    // an arrayUnion with no server-side check), which let any authenticated user
    // mark arbitrary quizzes "completed" on their own doc. Recorded here for every
    // finalized attempt — pass or fail — since that's what the old client write did.
    if (quizId) {
      tx.update(userRef, { completedQuizzes: FieldValue.arrayUnion(quizId) });
    }

    let finalScore = score;
    if (passed) {
      const displayName = userData.displayName ?? "CyberShield User";
      const moduleName = moduleSnap && moduleSnap.exists ? ((moduleSnap.data() as { title?: string }).title ?? "") : "";
      const quizTitle =
        quizSnap && quizSnap.exists ? ((quizSnap.data() as { title?: string }).title ?? "CyberShield Quiz") : "CyberShield Quiz";

      const certRef = userRef.collection("certificates").doc(resultId);
      tx.set(certRef, {
        id: resultId,
        userId: uid,
        userName: displayName,
        moduleId,
        moduleName,
        quizTitle,
        score,
        issuedAt: FieldValue.serverTimestamp(),
      });

      tx.update(userRef, { badges: FieldValue.arrayUnion("CyberDefender") });
      tx.set(leaderboardRef, { badges: FieldValue.arrayUnion("CyberDefender") }, { merge: true });
    } else {
      finalScore = 0;
    }

    const attemptResult: FinalizeQuizAttemptResult = {
      passed,
      score: finalScore,
      correctCount,
      percentage,
      xpEarned,
      alreadyAttempted,
    };
    tx.set(attemptRef, {
      ...attemptResult,
      finalizedAt: FieldValue.serverTimestamp(),
    });

    return attemptResult;
  });
}
