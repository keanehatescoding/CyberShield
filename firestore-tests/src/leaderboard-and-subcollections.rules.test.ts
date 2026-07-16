import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

// Covers the rest of the write-gap closures documented in firestore.rules:
// leaderboard/{userId} and the users/{userId} subcollections that are
// meant to be Cloud-Functions-only (quizResults, quizAttempts, certificates).

const PROJECT_ID = "cybershield-rules-test";
const OWNER_UID = "owner-uid";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync("../firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "leaderboard", OWNER_UID), {
      displayName: "Ada",
      xp: 100,
    });
    await setDoc(
      doc(context.firestore(), `users/${OWNER_UID}/quizResults/result-1`),
      { quizId: "quiz-1", isCorrect: true },
    );
    await setDoc(
      doc(context.firestore(), `users/${OWNER_UID}/quizAttempts/result-1`),
      { xpEarned: 10 },
    );
    await setDoc(
      doc(context.firestore(), `users/${OWNER_UID}/certificates/result-1`),
      { quizId: "quiz-1" },
    );
  });
});

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID).firestore();
}

describe("leaderboard/{userId} — public-safe mirror", () => {
  it("denies any client write, even by the matching-uid owner", async () => {
    const db = ownerDb();
    await assertFails(updateDoc(doc(db, "leaderboard", OWNER_UID), { xp: 999999 }));
  });

  it("allows any signed-in user to read", async () => {
    const db = testEnv.authenticatedContext("some-other-uid").firestore();
    await assertSucceeds(getDoc(doc(db, "leaderboard", OWNER_UID)));
  });
});

describe("users/{userId} server-only subcollections", () => {
  it.each(["quizResults", "quizAttempts", "certificates"])(
    "denies an owner write to %s",
    async (sub) => {
      const db = ownerDb();
      await assertFails(
        setDoc(doc(db, `users/${OWNER_UID}/${sub}/forged-doc`), { forged: true }),
      );
    },
  );

  it.each(["quizResults", "quizAttempts", "certificates"])(
    "allows the owner to read their own %s docs",
    async (sub) => {
      const db = ownerDb();
      await assertSucceeds(getDoc(doc(db, `users/${OWNER_UID}/${sub}/result-1`)));
    },
  );
});
