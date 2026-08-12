/**
 * One-off migration. Run once (via `npm run migrate:answerKeys`) against
 * your project before shipping the new client:
 *
 *   1. Reads every quizzes/{quizId}/questions/{questionId} doc.
 *   2. Writes its correctIndex + explanation into answerKeys/{quizId}_{questionId}.
 *   3. Removes correctIndex + explanation from the public question doc.
 *
 * After this runs, the public question doc only ever contains fields safe
 * to hand to an unauthenticated-of-answers client: text, options, order,
 * moduleId, moduleName, quizTitle.
 *
 * Safe to re-run — it's idempotent (uses set(), and deletes the fields with
 * FieldValue.delete() so a second pass on an already-migrated doc is a no-op).
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

initializeApp();
const db = getFirestore();

async function migrate() {
  const quizzes = await db.collection("quizzes").get();
  let migrated = 0;
  let repaired = 0;

  for (const quizDoc of quizzes.docs) {
    const questions = await quizDoc.ref.collection("questions").get();

    for (const q of questions.docs) {
      const data = q.data();
      // moduleId is a real, separate field on the question doc (quizId and
      // moduleId are not interchangeable — see Module.quizId in the client
      // domain model). It survives migration (only correctIndex/explanation
      // are deleted from the question doc below), so it's available to
      // repair an already-migrated answerKeys doc too, not just to migrate
      // a fresh one.
      const moduleId = typeof data.moduleId === "string" ? data.moduleId : undefined;

      if (data.correctIndex === undefined) {
        // Already migrated — but an earlier version of this script wrote
        // quizDoc.id (the wrong value) into moduleId instead of the
        // question's own moduleId field. Repair it in place rather than
        // silently leaving the corrupted value forever: this branch used to
        // just `continue`, which meant re-running the fixed script could
        // never fix answerKeys docs a prior buggy run had already touched.
        if (moduleId === undefined) {
          console.warn(`Question ${quizDoc.id}/${q.id} has already been migrated but has no moduleId; skipping repair.`);
          continue;
        }
        await db.collection("answerKeys").doc(`${quizDoc.id}_${q.id}`).set({ moduleId }, { merge: true });
        repaired++;
        continue;
      }

      const options: unknown[] = data.options ?? [];
      const batch = db.batch();

      batch.set(
        db.collection("answerKeys").doc(`${quizDoc.id}_${q.id}`),
        {
          correctIndex: data.correctIndex,
          optionCount: options.length,
          explanation: data.explanation ?? "",
          // Omit moduleId entirely when the question doc doesn't have one,
          // rather than writing an empty string over (and masking) whatever
          // might already be there from a previous partial write.
          ...(moduleId !== undefined ? { moduleId } : {}),
        },
        { merge: true },
      );

      batch.update(q.ref, {
        correctIndex: FieldValue.delete(),
        explanation: FieldValue.delete(),
      });

      await batch.commit();
      migrated++;
    }
  }

  console.log(`Migrated ${migrated} question(s) into answerKeys, repaired ${repaired} already-migrated answerKeys doc(s).`);
}

migrate()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error("Migration failed:", e);
    process.exit(1);
  });
