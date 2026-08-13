import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { isValidFirestoreDocId } from "./validation";

// completeModule only credits xp if the client-reported watch time covers at
// least this fraction of the module's declared duration. This does not stop
// a fully scripted client from fabricating a plausible watchedMs (any
// client-reported value can be lied about — see clampTimeRemaining in
// grading.ts for the same tradeoff), but it closes the zero-effort version
// of the exploit: without this, a signed-in client could loop over every
// `modules/{id}` (world-readable) and call this callable directly to farm
// the XP of the entire catalog without ever opening a lesson.
const MIN_WATCH_FRACTION = 0.85;

/**
 * Marks a module as completed for a user and awards its xpReward, entirely
 * server-side. Previously the client (ModuleViewModel.onVideoCompleted)
 * called UserRepository.addXp(uid, module.xpReward) directly — a plain
 * Firestore write gated only by `auth.uid == userId`, with no validation
 * that `points` actually matched a real module's xpReward. Any
 * authenticated client could award itself arbitrary XP this way, same root
 * cause as the quiz-XP hole fixed in finalizeQuizAttempt.
 *
 * Idempotent: re-completing an already-completed module is a no-op (no
 * double XP), matching the arrayUnion semantics the old client code relied on.
 */
export async function completeModule(
  uid: string,
  moduleId: string,
  watchedMs: number,
): Promise<{ alreadyCompleted: boolean; xpEarned: number }> {
  if (!isValidFirestoreDocId(moduleId)) {
    throw new HttpsError("invalid-argument", "moduleId must be a non-empty, valid document id.");
  }
  if (typeof watchedMs !== "number" || !Number.isFinite(watchedMs) || watchedMs < 0) {
    throw new HttpsError("invalid-argument", "watchedMs must be a non-negative number.");
  }

  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);
  const moduleRef = db.collection("modules").doc(moduleId);
  const leaderboardRef = db.collection("leaderboard").doc(uid);

  // Read-check-write must happen inside a single transaction, same as
  // finalizeQuizAttempt (see grading.ts). With plain get()-then-batch, two
  // concurrent calls for the same moduleId (double-tap, a retried request)
  // can both read completedModules missing moduleId before either commits,
  // and both go on to increment xp — arrayUnion is idempotent so the
  // module only appears once, but FieldValue.increment is not, so xp gets
  // awarded twice (or more, on every retry). A transaction makes Firestore
  // retry one of the two callers if their read sets overlap, so only one
  // ever observes the "not yet completed" state and proceeds to write.
  return db.runTransaction(async (tx) => {
    const [userSnap, moduleSnap] = await Promise.all([tx.get(userRef), tx.get(moduleRef)]);

    if (!moduleSnap.exists) {
      throw new HttpsError("not-found", "Module not found.");
    }

    const userData = (userSnap.data() ?? {}) as { completedModules?: string[] };
    if ((userData.completedModules ?? []).includes(moduleId)) {
      return { alreadyCompleted: true, xpEarned: 0 };
    }

    const moduleData = moduleSnap.data() as { xpReward?: number; durationMins?: number };
    // Number.isFinite (not just typeof), because NaN passes typeof === "number"
    // but every comparison against it (durationMins <= 0, watchedMs < ...)
    // evaluates to false — without this, an admin-side NaN in durationMins
    // would silently bypass the check below instead of failing closed.
    const durationMins = Number.isFinite(moduleData.durationMins) ? (moduleData.durationMins as number) : 0;
    const expectedMs = durationMins * 60_000;

    // Fail closed (like the expectedQuestionCount check in
    // finalizeQuizAttempt) rather than skipping the check when durationMins
    // is missing/zero — an admin-side data gap shouldn't turn into free XP.
    if (durationMins <= 0 || watchedMs < expectedMs * MIN_WATCH_FRACTION) {
      throw new HttpsError(
        "failed-precondition",
        "Module must be watched to completion before it can be marked complete.",
      );
    }

    const xpEarned = typeof moduleData.xpReward === "number" ? moduleData.xpReward : 0;

    tx.update(userRef, {
      completedModules: FieldValue.arrayUnion(moduleId),
      xp: FieldValue.increment(xpEarned),
    });
    tx.set(leaderboardRef, { xp: FieldValue.increment(xpEarned) }, { merge: true });

    return { alreadyCompleted: false, xpEarned };
  });
}
