import { describe, expect, it } from "vitest";
import { HttpsError } from "firebase-functions/v2/https";
import { assertValidAnswerInput, PASS_PERCENTAGE } from "./grading";

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
  ])("rejects %s", (_label, input) => {
    expect(() => assertValidAnswerInput(input)).toThrow(HttpsError);
  });
});

describe("PASS_PERCENTAGE", () => {
  it("matches the documented passing threshold used for certificate issuance", () => {
    expect(PASS_PERCENTAGE).toBe(70);
  });
});
