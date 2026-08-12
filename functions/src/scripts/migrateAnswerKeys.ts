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

  for (const quizDoc of quizzes.docs) {
    const questions = await quizDoc.ref.collection("questions").get();

    for (const q of questions.docs) {
      const data = q.data();
      if (data.correctIndex === undefined) {
        continue; // already migrated
      }

      const options: unknown[] = data.options ?? [];
      const batch = db.batch();

      batch.set(
        db.collection("answerKeys").doc(`${quizDoc.id}_${q.id}`),
        {
          correctIndex: data.correctIndex,
          optionCount: options.length,
          explanation: data.explanation ?? "",
          // moduleId is a real, separate field on the question doc (quizId
          // and moduleId are not interchangeable — see Module.quizId in the
          // client domain model), not the id of the quiz doc it's nested
          // under. Using quizDoc.id here previously wrote the wrong
          // moduleId into every migrated answerKeys doc, which then flowed
          // into quizResults.moduleId and corrupted certificate module-name
          // lookups whenever a quiz's id differed from its module's id.
          moduleId: data.moduleId ?? "",
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

  console.log(`Migrated ${migrated} question(s) into answerKeys.`);
}

migrate()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error("Migration failed:", e);
    process.exit(1);
  });
