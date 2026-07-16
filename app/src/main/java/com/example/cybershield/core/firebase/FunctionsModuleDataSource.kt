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
    internal constructor(
        /** Test seam — see FunctionsQuizDataSource's matching constructor for why this shape. */
        private val httpsCallable: suspend (name: String, payload: Map<String, Any?>) -> HttpsCallableResult,
    ) {
        @Inject
        constructor(functions: FirebaseFunctions) : this(
            { name, payload -> functions.getHttpsCallable(name).call(payload).await() },
        )

        suspend fun completeModule(moduleId: String): Result<ModuleCompleteResult> =
            try {
                val response = httpsCallable("completeModuleFn", hashMapOf("moduleId" to moduleId))

                val data = response.data.asCallableData("completeModuleFn")
                Result.Success(
                    ModuleCompleteResult(
                        alreadyCompleted = data.optBoolean("alreadyCompleted"),
                        xpEarned = data.optInt("xpEarned"),
                    ),
                )
            } catch (e: FirebaseFunctionsException) {
                Result.Error(Exception(e.message ?: "completeModule failed", e))
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
