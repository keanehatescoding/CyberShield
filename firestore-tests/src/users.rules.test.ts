import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

// Regression coverage for the users/{userId} write guard in firestore.rules.
//
// That rule blocks client writes that touch badges, xp, completedModules,
// or completedQuizzes — all four are meant to be written only by Cloud
// Functions (finalizeQuizAttemptFn / completeModuleFn) via the Admin SDK,
// which bypasses these rules entirely. completedQuizzes was the last of
// the four to be closed (see commit 596190f); this file exists so a future
// change to the rule can't silently reopen any of them without a red test.

const PROJECT_ID = "cybershield-rules-test";
const OWNER_UID = "owner-uid";
const OTHER_UID = "other-uid";

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
  // Seed a baseline users/{OWNER_UID} doc the way the server would leave it,
  // bypassing rules entirely (this is what the Admin SDK does in production).
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "users", OWNER_UID), {
      displayName: "Ada",
      xp: 100,
      badges: ["CyberDefender"],
      completedModules: ["module-1"],
      completedQuizzes: ["quiz-1"],
    });
  });
});

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID).firestore();
}

describe("users/{userId} write guard — server-only fields", () => {
  const guardedFieldCases: Array<[field: string, value: unknown]> = [
    ["completedQuizzes", ["quiz-1", "quiz-2"]],
    ["completedModules", ["module-1", "module-2"]],
    ["xp", 9999999],
    ["badges", ["CyberDefender", "SpeedRunner"]],
  ];

  it.each(guardedFieldCases)(
    "denies an owner update() that touches %s, even alongside otherwise-allowed fields",
    async (field, value) => {
      const db = ownerDb();
      await assertFails(
        updateDoc(doc(db, "users", OWNER_UID), {
          displayName: "Ada Lovelace",
          [field]: value,
        }),
      );
    },
  );

  it.each(guardedFieldCases)(
    "denies an owner set()-with-merge that touches %s",
    async (field, value) => {
      const db = ownerDb();
      await assertFails(
        setDoc(doc(db, "users", OWNER_UID), { [field]: value }, { merge: true }),
      );
    },
  );

  it("specifically denies a client arrayUnion-shaped write to completedQuizzes (the original exploit)", async () => {
    // The historical bug: UserRepositoryImpl.markQuizCompleted did a plain
    // client arrayUnion write here with no server-side check on which quiz
    // IDs were being added. Confirm that shape is rejected outright now,
    // independent of whatever value is supplied.
    const db = ownerDb();
    await assertFails(
      updateDoc(doc(db, "users", OWNER_UID), {
        completedQuizzes: ["any-quiz-id-a-malicious-client-likes"],
      }),
    );
  });

  it("still allows the owner to write ordinary profile fields untouched by the guard", async () => {
    const db = ownerDb();
    await assertSucceeds(
      updateDoc(doc(db, "users", OWNER_UID), {
        displayName: "Ada Lovelace",
        photoUrl: "https://example.com/ada.png",
      }),
    );
  });

  it("denies a non-owner from writing the doc at all, guarded field or not", async () => {
    const db = testEnv.authenticatedContext(OTHER_UID).firestore();
    await assertFails(
      updateDoc(doc(db, "users", OWNER_UID), { displayName: "Mallory" }),
    );
  });

  it("still allows the owner to read their own doc", async () => {
    const db = ownerDb();
    await assertSucceeds(getDoc(doc(db, "users", OWNER_UID)));
  });

  it("denies a non-owner from reading the doc", async () => {
    const db = testEnv.authenticatedContext(OTHER_UID).firestore();
    await assertFails(getDoc(doc(db, "users", OWNER_UID)));
  });
});
