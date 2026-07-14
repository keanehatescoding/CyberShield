import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

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
): Promise<{ alreadyCompleted: boolean; xpEarned: number }> {
  if (!moduleId || typeof moduleId !== "string") {
    throw new HttpsError("invalid-argument", "moduleId is required.");
  }

  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);
  const moduleRef = db.collection("modules").doc(moduleId);

  const [userSnap, moduleSnap] = await Promise.all([userRef.get(), moduleRef.get()]);

  if (!moduleSnap.exists) {
    throw new HttpsError("not-found", "Module not found.");
  }

  const userData = (userSnap.data() ?? {}) as { completedModules?: string[] };
  if ((userData.completedModules ?? []).includes(moduleId)) {
    return { alreadyCompleted: true, xpEarned: 0 };
  }

  const moduleData = moduleSnap.data() as { xpReward?: number };
  const xpEarned = typeof moduleData.xpReward === "number" ? moduleData.xpReward : 0;

  const leaderboardRef = db.collection("leaderboard").doc(uid);
  const batch = db.batch();
  batch.update(userRef, {
    completedModules: FieldValue.arrayUnion(moduleId),
    xp: FieldValue.increment(xpEarned),
  });
  batch.set(leaderboardRef, { xp: FieldValue.increment(xpEarned) }, { merge: true });
  await batch.commit();

  return { alreadyCompleted: false, xpEarned };
}
