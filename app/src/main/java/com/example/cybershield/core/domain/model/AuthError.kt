package com.example.cybershield.core.domain.model

/**
 * Domain-level representation of everything that can go wrong during auth.
 * Lets the UI layer (ViewModel/Composable) decide how to phrase the message,
 * instead of baking user-facing strings into the data layer.
 *
 * Extends Exception (rather than being a plain sealed class) so it fits directly
 * into the existing `Result.Error(exception: Exception)` shape with no changes
 * to core.domain.util.Result.
 */
sealed class AuthError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    data object InvalidCredentials : AuthError("Incorrect email or password.")

    data object UserNotFound : AuthError("No account found with this email.")

    data object EmailAlreadyInUse : AuthError("This email is already registered.")

    data object TooManyRequests : AuthError("Too many attempts. Please wait and try again.")

    data object InvalidDisplayName : AuthError("Please enter a name between 1 and 60 characters.")

    data object NoNetwork : AuthError("No internet connection.")

    data class Unknown(
        val originalCause: Throwable? = null,
    ) : AuthError("Something went wrong. Please try again.", originalCause)
}
