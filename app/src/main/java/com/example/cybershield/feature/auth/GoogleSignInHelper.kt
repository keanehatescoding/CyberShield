package com.example.cybershield.feature.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.example.cybershield.R.string.web_client_id

@Singleton
class GoogleSignInHelper @Inject constructor(
    @ApplicationContext private val context: Context
){
    private val credentialManager = CredentialManager.create(context)
    suspend fun signIn(activityContext: Context):Result<String>{
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(web_client_id.toString())
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleCredential.idToken)
            } else{
                Result.failure(Exception("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("Sign-in cancelled"))
        } catch (e: GetCredentialException) {
            Result.failure(e)
        }
    }
    suspend fun signOut() =
        try {
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest()
            )
        } catch (_: Exception) { /* safe to ignore */ }
}