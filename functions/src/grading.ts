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
    !Number.isInteger(a.selectedIndex)
  ) {
    throw new HttpsError("invalid-argument", "quizId, questionId, and an integer selectedIndex are required.");
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
      selectedIndex,
      isCorrect: graded.isCorrect,
      clientAnsweredAt: clientAnsweredAt ?? null,
      validatedAt: FieldValue.serverTimestamp(),
      validatedBy: "cloudFunction",
    },
    { merge: true },
  );
}
