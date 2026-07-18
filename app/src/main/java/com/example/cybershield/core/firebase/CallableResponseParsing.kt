package com.example.cybershield.core.firebase

/**
 * Thrown when a Cloud Functions callable response doesn't have the shape the
 * client expects (missing key, or wrong type). Cloud Functions responses are
 * decoded from JSON into `Map<String, Any?>` with no compile-time guarantee
 * about their shape, so a server-side change or a bug in a specific function
 * can otherwise surface as an opaque [ClassCastException] deep inside data
 * parsing. This gives callers (and crash reports) a message that names the
 * field and the function that produced it.
 */
class MalformedCallableResponseException(
    functionName: String,
    field: String,
    reason: String,
) : Exception("Malformed response from '$functionName': field '$field' $reason")

/** Top-level response body of a callable, e.g. `response.data as Map<String, Any?>`. */
typealias CallableData = Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.asCallableData(functionName: String): CallableData =
    this as? CallableData
        ?: throw MalformedCallableResponseException(functionName, "<root>", "was not an object")

fun CallableData.requireString(
    key: String,
    functionName: String,
): String =
    this[key] as? String
        ?: throw MalformedCallableResponseException(functionName, key, "was missing or not a string")

fun CallableData.optString(key: String): String? = this[key] as? String

fun CallableData.requireBoolean(
    key: String,
    functionName: String,
): Boolean =
    this[key] as? Boolean
        ?: throw MalformedCallableResponseException(functionName, key, "was missing or not a boolean")

fun CallableData.optBoolean(
    key: String,
    default: Boolean = false,
): Boolean = this[key] as? Boolean ?: default

fun CallableData.requireInt(
    key: String,
    functionName: String,
): Int =
    (this[key] as? Number)?.toInt()
        ?: throw MalformedCallableResponseException(functionName, key, "was missing or not a number")

fun CallableData.optInt(
    key: String,
    default: Int = 0,
): Int = (this[key] as? Number)?.toInt() ?: default

@Suppress("UNCHECKED_CAST")
fun CallableData.requireMapList(
    key: String,
    functionName: String,
): List<CallableData> =
    this[key] as? List<CallableData>
        ?: throw MalformedCallableResponseException(functionName, key, "was missing or not a list of objects")
