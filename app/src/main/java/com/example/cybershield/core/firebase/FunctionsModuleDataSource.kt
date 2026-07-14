package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.repository.ModuleCompleteResult
import com.example.cybershield.core.domain.util.Result
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to completeModuleFn — the ONLY writer of xp/completedModules for
 * the module-completion reward path. See UserRepository.completeModule
 * kdoc for why this moved server-side.
 */
@Singleton
class FunctionsModuleDataSource
    @Inject
    constructor(
        private val functions: FirebaseFunctions,
    ) {
        /** Test seam — see FunctionsQuizDataSource.httpsCallable for why this shape. */
        internal var httpsCallable: suspend (name: String, payload: Map<String, Any?>) -> HttpsCallableResult =
            { name, payload -> functions.getHttpsCallable(name).call(payload).await() }

        suspend fun completeModule(moduleId: String): Result<ModuleCompleteResult> =
            try {
                val response = httpsCallable("completeModuleFn", hashMapOf("moduleId" to moduleId))

                @Suppress("UNCHECKED_CAST")
                val data = response.data as Map<String, Any?>
                Result.Success(
                    ModuleCompleteResult(
                        alreadyCompleted = data["alreadyCompleted"] as? Boolean ?: false,
                        xpEarned = (data["xpEarned"] as? Number)?.toInt() ?: 0,
                    ),
                )
            } catch (e: FirebaseFunctionsException) {
                Result.Error(Exception(e.message ?: "completeModule failed", e))
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
