const FIRESTORE_DOC_ID_MAX_BYTES = 1500;

/**
 * Checks whether [id] is safe to use as a single Firestore document id
 * segment — i.e. the kind of value that can go straight into
 * `collection.doc(id)` or be interpolated into a compound doc id like
 * `${quizId}_${questionId}` without silently changing which document gets
 * read or written.
 *
 * Mirrors Firestore's own doc-id constraints (see
 * https://firebase.google.com/docs/firestore/quotas#limits): non-empty,
 * <= 1500 bytes UTF-8, must not contain "/" (a "/" turns what the caller
 * intended as one id into extra path segments — collection/document/
 * collection/... — which either resolves to a different document than
 * intended or throws deep inside the SDK), must not be exactly "." or
 * "..", and must not match the reserved `__*__` pattern.
 *
 * This is a defense-in-depth / error-quality check, not a security
 * boundary: every caller of this function already requires
 * `request.auth`, and Firestore rejects genuinely malformed paths on its
 * own. The point is to fail fast with a clean `invalid-argument` instead
 * of a raw SDK exception.
 */
export function isValidFirestoreDocId(id: unknown): id is string {
  if (typeof id !== "string" || id.length === 0) return false;
  if (id === "." || id === "..") return false;
  if (id.includes("/")) return false;
  if (/^__.*__$/.test(id)) return false;
  if (Buffer.byteLength(id, "utf8") > FIRESTORE_DOC_ID_MAX_BYTES) return false;
  return true;
}
