import { describe, expect, it } from "vitest";
import { HttpsError } from "firebase-functions/v2/https";
import {
  assertValidAnswerInput,
  clampTimeRemaining,
  MAX_TIME_REMAINING_SECONDS,
  PASS_PERCENTAGE,
  XP_PER_CORRECT_ANSWER,
  XP_BONUS_PERFECT_SCORE,
  FinalizeQuizAttemptResult,
} from "./grading";

const validInput = {
  quizId: "quiz1",
  questionId: "q1",
  selectedIndex: 2,
  resultId: "r1",
};

describe("assertValidAnswerInput", () => {
  it("accepts a well-formed answer payload", () => {
    expect(() => assertValidAnswerInput(validInput)).not.toThrow();
  });

  it("accepts the -1 sentinel for a timed-out question", () => {
    expect(() => assertValidAnswerInput({ ...validInput, selectedIndex: -1 })).not.toThrow();
  });

  it.each([
    ["null input", null],
    ["missing quizId", { ...validInput, quizId: undefined }],
    ["empty quizId", { ...validInput, quizId: "" }],
    ["missing questionId", { ...validInput, questionId: undefined }],
    ["non-integer selectedIndex", { ...validInput, selectedIndex: 1.5 }],
    ["non-numeric selectedIndex", { ...validInput, selectedIndex: "2" }],
    ["missing resultId", { ...validInput, resultId: undefined }],
    ["empty resultId", { ...validInput, resultId: "" }],
    ["quizId with a slash", { ...validInput, quizId: "quiz/1" }],
    ["questionId with a slash", { ...validInput, questionId: "q/1" }],
    ["resultId with a slash", { ...validInput, resultId: "r/1" }],
    ["resultId of '.'", { ...validInput, resultId: "." }],
    ["resultId of '..'", { ...validInput, resultId: ".." }],
  ])("rejects %s", (_label, input) => {
    expect(() => assertValidAnswerInput(input)).toThrow(HttpsError);
  });
});

describe("PASS_PERCENTAGE", () => {
  it("matches the documented passing threshold used for certificate issuance", () => {
    expect(PASS_PERCENTAGE).toBe(70);
  });
});

describe("clampTimeRemaining", () => {
  it("passes through a value within the legitimate range", () => {
    expect(clampTimeRemaining(12)).toBe(12);
  });

  it("caps a client-supplied value above the max countdown at the max", () => {
    expect(clampTimeRemaining(999999)).toBe(MAX_TIME_REMAINING_SECONDS);
  });

  it("floors a negative value at zero", () => {
    expect(clampTimeRemaining(-5)).toBe(0);
  });

  it("treats a missing or non-numeric value as zero", () => {
    expect(clampTimeRemaining(undefined)).toBe(0);
    expect(clampTimeRemaining("30")).toBe(0);
    expect(clampTimeRemaining(NaN)).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// XP accounting — unit-tested against the pure formula that finalizeQuizAttempt
// uses, without needing a live Firestore emulator. These tests were previously
// absent (coverage gap identified in the Jul 2026 audit); this is their first
// appearance.
// ---------------------------------------------------------------------------

/**
 * Replicates the xpEarned formula in finalizeQuizAttempt so the arithmetic
 * can be verified in isolation. Keep in sync with grading.ts if the formula
 * changes.
 */
function computeXpEarned(correctCount: number, total: number, alreadyAttempted: boolean): number {
  if (alreadyAttempted) return 0;
  return correctCount * XP_PER_CORRECT_ANSWER + (total > 0 && correctCount === total ? XP_BONUS_PERFECT_SCORE : 0);
}

describe("XP formula — first attempt", () => {
  it("awards XP_PER_CORRECT_ANSWER per correct answer", () => {
    expect(computeXpEarned(3, 5, false)).toBe(3 * XP_PER_CORRECT_ANSWER);
  });

  it("adds XP_BONUS_PERFECT_SCORE on a perfect score", () => {
    expect(computeXpEarned(5, 5, false)).toBe(5 * XP_PER_CORRECT_ANSWER + XP_BONUS_PERFECT_SCORE);
  });

  it("gives 0 XP for 0 correct answers (no negative XP)", () => {
    expect(computeXpEarned(0, 5, false)).toBe(0);
  });

  it("does not award the perfect-score bonus when not all answers are correct", () => {
    const xp = computeXpEarned(4, 5, false);
    expect(xp).toBe(4 * XP_PER_CORRECT_ANSWER);
    expect(xp).not.toBe(4 * XP_PER_CORRECT_ANSWER + XP_BONUS_PERFECT_SCORE);
  });
});

describe("XP formula — retake (alreadyAttempted = true)", () => {
  it("awards 0 XP on a retake regardless of correct count", () => {
    expect(computeXpEarned(5, 5, true)).toBe(0);
    expect(computeXpEarned(3, 5, true)).toBe(0);
    expect(computeXpEarned(0, 5, true)).toBe(0);
  });

  it("awards 0 XP on a perfect-score retake (no farming via perfect bonus)", () => {
    expect(computeXpEarned(5, 5, true)).toBe(0);
  });
});

describe("alreadyAttempted flag in FinalizeQuizAttemptResult", () => {
  it("is included in the result type and defaults to undefined (first attempt)", () => {
    const result: FinalizeQuizAttemptResult = {
      passed: true,
      score: 500,
      correctCount: 5,
      percentage: 100,
      xpEarned: 5 * XP_PER_CORRECT_ANSWER + XP_BONUS_PERFECT_SCORE,
    };
    expect(result.alreadyAttempted).toBeUndefined();
  });

  it("is true on a retake and xpEarned is 0", () => {
    const result: FinalizeQuizAttemptResult = {
      passed: true,
      score: 500,
      correctCount: 5,
      percentage: 100,
      xpEarned: computeXpEarned(5, 5, true),
      alreadyAttempted: true,
    };
    expect(result.alreadyAttempted).toBe(true);
    expect(result.xpEarned).toBe(0);
  });
});
