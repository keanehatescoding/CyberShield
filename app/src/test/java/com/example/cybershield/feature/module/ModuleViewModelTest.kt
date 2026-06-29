package com.example.cybershield.feature.module

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.usecase.module.GetModuleByIdUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.fake.FakeModuleRepository
import com.example.cybershield.core.testing.fake.FakeUserRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import com.example.cybershield.feature.modules.ModuleViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ModuleViewModelTest {
    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var moduleRepository: FakeModuleRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var getModuleByIdUseCase: GetModuleByIdUseCase
    private lateinit var getCurrentSession: GetCurrentSessionUseCase

    private val testUid = "test-uid" // matches FakeUserRepository's default fakeUser.uid

    private val testModule =
        Module(
            id = "module-1",
            title = "Phishing Basics",
            xpReward = 50,
        )

    private fun savedStateHandleFor(moduleId: String): SavedStateHandle = SavedStateHandle(mapOf("moduleId" to moduleId))

    @Before
    fun setup() {
        moduleRepository = FakeModuleRepository()
        userRepository = FakeUserRepository()
        authRepository = mockk()

        every { authRepository.currentSession() } returns
            AuthRepository.AuthSession(
                uid = testUid,
                email = "test@cybershield.com",
                isEmailVerified = true,
            )

        getModuleByIdUseCase = GetModuleByIdUseCase(moduleRepository)
        getCurrentSession = GetCurrentSessionUseCase(authRepository)
    }

    private fun createViewModel(moduleId: String = testModule.id): ModuleViewModel =
        ModuleViewModel(
            getModuleByIdUseCase = getModuleByIdUseCase,
            moduleRepository = moduleRepository,
            userRepository = userRepository,
            getCurrentSession = getCurrentSession,
            savedStateHandle = savedStateHandleFor(moduleId),
        )

    // ---------- loadModule() ----------

    @Test
    fun `loadModule emits loading then success with module data`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            userRepository.fakeUser = userRepository.fakeUser.copy(completedModules = emptyList())

            val viewModel = createViewModel()

            viewModel.uiState.test {
                val loading = awaitItem()
                assertTrue(loading.isLoading)

                val success = awaitItem()
                assertFalse(success.isLoading)
                assertEquals(testModule, success.module)
                assertFalse(success.isAlreadyCompleted)
                assertFalse(success.isStale)
                assertNull(success.error)
            }
        }

    @Test
    fun `loadModule marks isAlreadyCompleted true when module id is in user's completedModules`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            userRepository.fakeUser = userRepository.fakeUser.copy(completedModules = listOf(testModule.id))

            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem() // loading
                val success = awaitItem()
                assertTrue(success.isAlreadyCompleted)
            }
        }

    @Test
    fun `loadModule surfaces stale flag without setting an error message`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = {
                flowOf(Result.Error(Exception("cache stale"), isStale = true))
            }

            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem() // loading
                val staleState = awaitItem()
                assertFalse(staleState.isLoading)
                assertTrue(staleState.isStale)
                assertNull(staleState.error)
            }
        }

    @Test
    fun `loadModule surfaces error message on non-stale error`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = {
                flowOf(Result.Error(Exception("network down"), isStale = false))
            }

            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem() // loading
                val errorState = awaitItem()
                assertFalse(errorState.isLoading)
                assertFalse(errorState.isStale)
                assertEquals("network down", errorState.error)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `calling loadModule again replaces state from the previous in-flight collection`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flow { /* never emits */ } }

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isLoading)

            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            userRepository.fakeUser = userRepository.fakeUser.copy(completedModules = emptyList())
            viewModel.loadModule()
            advanceUntilIdle()

            assertEquals(testModule, viewModel.uiState.value.module)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    // ---------- loadSavedPosition() (runs in init) ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `init loads saved playback position and marks it loaded`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            moduleRepository.seedPlaybackPosition(testModule.id, testUid, 4_200L)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(4_200L, viewModel.savedPositionMs.value)
            assertTrue(viewModel.isSavedPositionLoaded.value)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `init defaults saved position to zero when nothing is stored`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(0L, viewModel.savedPositionMs.value)
            assertTrue(viewModel.isSavedPositionLoaded.value)
        }

    // ---------- savePosition() ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `savePosition delegates to repository with moduleId, uid, and position`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.savePosition(9_000L)
            advanceUntilIdle()

            assertEquals(
                listOf(Triple(testModule.id, testUid, 9_000L)),
                moduleRepository.savePlaybackPositionCalls,
            )
        }

    // ---------- onVideoCompleted() ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onVideoCompleted marks module complete, awards xp, and shows dialog when not already completed`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            userRepository.fakeUser = userRepository.fakeUser.copy(completedModules = emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onVideoCompleted()
            advanceUntilIdle()

            assertTrue(testModule.id in userRepository.completedModuleIds)
            assertEquals(testModule.xpReward, userRepository.totalXpAdded)
            assertTrue(viewModel.uiState.value.showCompletionDialog)
            assertTrue(viewModel.uiState.value.isAlreadyCompleted)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onVideoCompleted is a no-op when module is already completed`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            userRepository.fakeUser = userRepository.fakeUser.copy(completedModules = listOf(testModule.id))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onVideoCompleted()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showCompletionDialog)
            assertTrue(userRepository.completedModuleIds.isEmpty())
            assertEquals(0, userRepository.totalXpAdded)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onVideoCompleted does nothing when module has not loaded yet`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flow { /* never emits */ } }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onVideoCompleted()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showCompletionDialog)
            assertTrue(userRepository.completedModuleIds.isEmpty())
        }

    // ---------- onCompletionDialogDismissed() ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onCompletionDialogDismissed hides the dialog`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }
            userRepository.fakeUser = userRepository.fakeUser.copy(completedModules = emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onVideoCompleted()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showCompletionDialog)

            viewModel.onCompletionDialogDismissed()

            assertFalse(viewModel.uiState.value.showCompletionDialog)
        }

    // ---------- setPlaybackSpeed() ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `setPlaybackSpeed updates playbackSpeed state`() =
        runTest {
            moduleRepository.getModuleByIdFlowProvider = { flowOf(Result.Success(testModule)) }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setPlaybackSpeed(1.5f)

            assertEquals(1.5f, viewModel.playbackSpeed.value)
        }
}
