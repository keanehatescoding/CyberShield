import { describe, expect, it } from "vitest";
import { isValidFirestoreDocId } from "./validation";

describe("isValidFirestoreDocId", () => {
  it.each([
    ["a simple alphanumeric id", "quiz1"],
    ["an id with dashes and underscores", "quiz-1_final"],
    ["a single dot surrounded by other characters", "a.b"],
  ])("accepts %s", (_label, id) => {
    expect(isValidFirestoreDocId(id)).toBe(true);
  });

  it.each([
    ["undefined", undefined],
    ["null", null],
    ["a number", 123],
    ["an empty string", ""],
    ["exactly a single dot", "."],
    ["exactly two dots", ".."],
    ["a slash in the middle", "quiz/1"],
    ["a leading slash", "/quiz1"],
    ["a reserved __*__ pattern", "__reserved__"],
    ["a value over 1500 bytes", "a".repeat(1501)],
  ])("rejects %s", (_label, id) => {
    expect(isValidFirestoreDocId(id)).toBe(false);
  });
});
