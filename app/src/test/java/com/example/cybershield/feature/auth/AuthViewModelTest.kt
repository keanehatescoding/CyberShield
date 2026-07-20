package com.example.cybershield.feature.auth

import app.cash.turbine.test
import com.example.cybershield.core.domain.model.AuthError
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.usecase.auth.CheckEmailVerifiedUseCase
import com.example.cybershield.core.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.cybershield.core.domain.usecase.auth.RegisterUseCase
import com.example.cybershield.core.domain.usecase.auth.ResendVerificationEmailUseCase
import com.example.cybershield.core.domain.usecase.auth.SignInUseCase
import com.example.cybershield.core.domain.usecase.auth.SignOutUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.sync.FcmTokenSyncTrigger
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Unit tests for [AuthViewModel].
 *
 * The ViewModel only depends on use cases, so we fake/mock those directly rather than
 * standing up [AuthRepository] or Firebase — that surface is covered by
 * AuthRepositoryImpl's own tests.
 *
 * NOTE on [SignOutUseCase]: it's suspend — it clears the local Room DB after signing out of
 * Firebase (see its kdoc) — so `signOutUseCase()` is stubbed with `coEvery` below, and
 * `viewModel.signOut()` (which now launches in viewModelScope) is exercised inside
 * `runTest { }` with `advanceUntilIdle()`. The assertions themselves are unchanged.
 */
class AuthViewModelTest {
    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val observeAuthState: ObserveAuthStateUseCase = mockk()
    private val registerUseCase: RegisterUseCase = mockk()
    private val signInUseCase: SignInUseCase = mockk()
    private val resendVerificationEmailUseCase: ResendVerificationEmailUseCase = mockk()
    private val checkEmailVerifiedUseCase: CheckEmailVerifiedUseCase = mockk()
    private val signOutUseCase: SignOutUseCase = mockk()
    private val fcmTokenSyncTrigger: FcmTokenSyncTrigger =
        mockk {
            coEvery { syncCurrentToken() } returns Unit
        }

    private fun session(
        uid: String = "uid-123",
        email: String? = "person@example.com",
        isEmailVerified: Boolean = true,
    ) = AuthRepository.AuthSession(uid = uid, email = email, isEmailVerified = isEmailVerified)

    /**
     * Builds the ViewModel. [observeAuthState.currentSession()] must be stubbed by the
     * caller *before* invoking this, since it's read synchronously inside init {}.
     */
    private fun buildViewModel(): AuthViewModel =
        AuthViewModel(
            observeAuthState = observeAuthState,
            registerUseCase = registerUseCase,
            signInUseCase = signInUseCase,
            resendVerificationEmailUseCase = resendVerificationEmailUseCase,
            checkEmailVerifiedUseCase = checkEmailVerifiedUseCase,
            signOutUseCase = signOutUseCase,
            fcmTokenSyncTrigger = fcmTokenSyncTrigger,
        )

    // ---------------------------------------------------------------------
    // init { } — initial state resolution from currentSession()
    // ---------------------------------------------------------------------

    @Test
    fun `init with no session emits SignedOut`() {
        every { observeAuthState.currentSession() } returns null

        val viewModel = buildViewModel()

        assertEquals(AuthState.SignedOut(), viewModel.state.value)
    }

    @Test
    fun `init with unverified session emits AwaitingEmailVerification`() {
        every { observeAuthState.currentSession() } returns
            session(
                email = "person@example.com",
                isEmailVerified = false,
            )

        val viewModel = buildViewModel()

        assertEquals(
            AuthState.AwaitingEmailVerification(email = "person@example.com"),
            viewModel.state.value,
        )
    }

    @Test
    fun `init with unverified session and null email falls back to empty string`() {
        every { observeAuthState.currentSession() } returns
            session(
                email = null,
                isEmailVerified = false,
            )

        val viewModel = buildViewModel()

        assertEquals(
            AuthState.AwaitingEmailVerification(email = ""),
            viewModel.state.value,
        )
    }

    @Test
    fun `init with verified session emits Authenticated`() {
        every { observeAuthState.currentSession() } returns
            session(
                uid = "uid-999",
                isEmailVerified = true,
            )

        val viewModel = buildViewModel()

        assertEquals(AuthState.Authenticated("uid-999"), viewModel.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `init with verified session syncs the FCM token`() =
        runTest {
            // Covers an already-authenticated cold start: a token issued before
            // this session existed (e.g. pre-login on a prior run) must still
            // get attached once there's a uid to attach it to.
            every { observeAuthState.currentSession() } returns session(uid = "uid-999", isEmailVerified = true)

            buildViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { fcmTokenSyncTrigger.syncCurrentToken() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `init with no session does not sync the FCM token`() =
        runTest {
            every { observeAuthState.currentSession() } returns null

            buildViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { fcmTokenSyncTrigger.syncCurrentToken() }
        }

    // ---------------------------------------------------------------------
    // register()
    // ---------------------------------------------------------------------

    @Test
    fun `register success transitions SignedOut to AwaitingEmailVerification`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { registerUseCase("Jane", "jane@example.com", "pw123456") } returns Result.Success(Unit)

            val viewModel = buildViewModel()
            viewModel.state.test {
                assertEquals(AuthState.SignedOut(), awaitItem())

                viewModel.register("Jane", "jane@example.com", "pw123456")

                val loading = awaitItem()
                assertEquals(AuthState.SignedOut(isLoading = true, error = null), loading)

                val success = awaitItem()
                assertEquals(AuthState.AwaitingEmailVerification(email = "jane@example.com"), success)
            }
        }

    @Test
    fun `register failure surfaces error message and returns to SignedOut`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { registerUseCase(any(), any(), any()) } returns
                Result.Error(AuthError.EmailAlreadyInUse)

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem() // initial SignedOut()

                viewModel.register("Jane", "jane@example.com", "pw123456")

                awaitItem() // loading = true

                val failed = awaitItem() as AuthState.SignedOut
                assertEquals(false, failed.isLoading)
                assertEquals("This email is already registered.", failed.error)
            }
        }

    @Test
    fun `register falls back to default message when exception has a null message`() =
        runTest {
            // AuthError always supplies a non-null message, so this exercises the `?:` fallback
            // in AuthViewModel.fail() using a raw exception, simulating an unmapped failure that
            // slipped through as something other than a typed AuthError.
            every { observeAuthState.currentSession() } returns null
            coEvery { registerUseCase(any(), any(), any()) } returns
                Result.Error(IllegalStateException(null as String?))

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem() // initial
                viewModel.register("Jane", "jane@example.com", "pw123456")
                awaitItem() // loading

                val failed = awaitItem() as AuthState.SignedOut
                assertEquals("Something went wrong. Please try again.", failed.error)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `register is a no-op when state is not SignedOut`() =
        runTest {
            every { observeAuthState.currentSession() } returns session(isEmailVerified = true)
            coEvery { registerUseCase(any(), any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()
            val before = viewModel.state.value
            assertTrue(before is AuthState.Authenticated)

            viewModel.register("Jane", "jane@example.com", "pw123456")
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
            coVerify(exactly = 0) { registerUseCase(any(), any(), any()) }
        }

    // ---------------------------------------------------------------------
    // signIn()
    // ---------------------------------------------------------------------

    @Test
    fun `signIn success with verified email emits Authenticated`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signInUseCase("a@b.com", "pw") } returns
                Result.Success(session(uid = "uid-42", isEmailVerified = true))

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem() // SignedOut()
                viewModel.signIn("a@b.com", "pw")
                awaitItem() // loading
                assertEquals(AuthState.Authenticated("uid-42"), awaitItem())
            }
        }

    @Test
    fun `signIn success with unverified email emits AwaitingEmailVerification`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signInUseCase("a@b.com", "pw") } returns
                Result.Success(session(email = "a@b.com", isEmailVerified = false))

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem()
                viewModel.signIn("a@b.com", "pw")
                awaitItem() // loading
                assertEquals(
                    AuthState.AwaitingEmailVerification(email = "a@b.com"),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `signIn success with unverified email and null session email falls back to argument`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signInUseCase("a@b.com", "pw") } returns
                Result.Success(session(email = null, isEmailVerified = false))

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem()
                viewModel.signIn("a@b.com", "pw")
                awaitItem() // loading
                assertEquals(
                    AuthState.AwaitingEmailVerification(email = "a@b.com"),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `signIn failure keeps SignedOut with error and clears loading`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signInUseCase(any(), any()) } returns
                Result.Error(AuthError.InvalidCredentials)

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem()
                viewModel.signIn("a@b.com", "wrong")
                awaitItem() // loading = true

                val failed = awaitItem() as AuthState.SignedOut
                assertEquals(false, failed.isLoading)
                assertEquals("Incorrect email or password.", failed.error)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signIn success with verified email syncs the FCM token`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signInUseCase("a@b.com", "pw") } returns
                Result.Success(session(uid = "uid-42", isEmailVerified = true))

            val viewModel = buildViewModel()
            viewModel.signIn("a@b.com", "pw")
            advanceUntilIdle()

            coVerify(exactly = 1) { fcmTokenSyncTrigger.syncCurrentToken() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signIn success with unverified email does not sync the FCM token`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signInUseCase("a@b.com", "pw") } returns
                Result.Success(session(email = "a@b.com", isEmailVerified = false))

            val viewModel = buildViewModel()
            viewModel.signIn("a@b.com", "pw")
            advanceUntilIdle()

            coVerify(exactly = 0) { fcmTokenSyncTrigger.syncCurrentToken() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signIn is a no-op when state is not SignedOut`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(email = "x@x.com", isEmailVerified = false)

            val viewModel = buildViewModel()
            val before = viewModel.state.value

            viewModel.signIn("a@b.com", "pw")
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
        }

    // ---------------------------------------------------------------------
    // resendVerificationEmail() + cooldown timer
    // ---------------------------------------------------------------------

    @Test
    fun `resendVerificationEmail success starts 60s cooldown`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(email = "a@b.com", isEmailVerified = false)
            coEvery { resendVerificationEmailUseCase() } returns Result.Success(Unit)

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem() // initial AwaitingEmailVerification

                viewModel.resendVerificationEmail()

                val resending = awaitItem() as AuthState.AwaitingEmailVerification
                assertTrue(resending.isResending)

                val started = awaitItem() as AuthState.AwaitingEmailVerification
                assertEquals(false, started.isResending)
                assertEquals(60, started.resendCooldownSeconds)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `resendVerificationEmail cooldown counts down to zero`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(email = "a@b.com", isEmailVerified = false)
            coEvery { resendVerificationEmailUseCase() } returns Result.Success(Unit)

            val viewModel = buildViewModel()
            viewModel.resendVerificationEmail()
            advanceUntilIdle() // let resend complete + run the full 60s countdown

            val finalState = viewModel.state.value as AuthState.AwaitingEmailVerification
            assertEquals(0, finalState.resendCooldownSeconds)
            assertEquals(false, finalState.isResending)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `resendVerificationEmail cooldown decrements one second at a time`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(email = "a@b.com", isEmailVerified = false)
            coEvery { resendVerificationEmailUseCase() } returns Result.Success(Unit)

            val viewModel = buildViewModel()
            viewModel.resendVerificationEmail()
            advanceTimeBy(1.milliseconds) // resolve the resend call itself, cooldown starts at 60

            // Advance ~5 seconds of virtual time into the countdown.
            advanceTimeBy(5_000.milliseconds)

            val midState = viewModel.state.value as AuthState.AwaitingEmailVerification
            assertTrue(midState.resendCooldownSeconds in 54..59)
        }

    @Test
    fun `resendVerificationEmail failure clears isResending, starts no cooldown, and surfaces the error`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(email = "a@b.com", isEmailVerified = false)
            coEvery { resendVerificationEmailUseCase() } returns
                Result.Error(AuthError.TooManyRequests)

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem() // initial

                viewModel.resendVerificationEmail()
                awaitItem() // isResending = true

                val after = awaitItem() as AuthState.AwaitingEmailVerification
                assertEquals(false, after.isResending)
                assertEquals(0, after.resendCooldownSeconds)
                assertEquals("Too many attempts. Please wait and try again.", after.error)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `resendVerificationEmail is a no-op when state is not AwaitingEmailVerification`() =
        runTest {
            every { observeAuthState.currentSession() } returns null

            val viewModel = buildViewModel()
            val before = viewModel.state.value

            viewModel.resendVerificationEmail()
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `calling resendVerificationEmail again restarts the cooldown timer`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(email = "a@b.com", isEmailVerified = false)
            coEvery { resendVerificationEmailUseCase() } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.resendVerificationEmail()
            advanceTimeBy(10_000.milliseconds) // 10s into the first cooldown

            viewModel.resendVerificationEmail() // should cancel old job, restart at 60
            advanceUntilIdle()

            val finalState = viewModel.state.value as AuthState.AwaitingEmailVerification
            // Old job was canceled and a fresh one ran to completion.
            assertEquals(0, finalState.resendCooldownSeconds)
        }

    // ---------------------------------------------------------------------
    // checkEmailVerified()
    // ---------------------------------------------------------------------

    @Test
    fun `checkEmailVerified true transitions to Authenticated`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(uid = "uid-7", email = "a@b.com", isEmailVerified = false)
            coEvery { checkEmailVerifiedUseCase() } returns Result.Success(true)

            val viewModel = buildViewModel()
            viewModel.state.test {
                awaitItem() // AwaitingEmailVerification
                viewModel.checkEmailVerified()
                assertEquals(AuthState.Authenticated("uid-7"), awaitItem())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `checkEmailVerified true syncs the FCM token`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(uid = "uid-7", email = "a@b.com", isEmailVerified = false)
            coEvery { checkEmailVerifiedUseCase() } returns Result.Success(true)

            val viewModel = buildViewModel()
            viewModel.checkEmailVerified()
            advanceUntilIdle()

            coVerify(exactly = 1) { fcmTokenSyncTrigger.syncCurrentToken() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `checkEmailVerified false leaves state unchanged`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(uid = "uid-7", email = "a@b.com", isEmailVerified = false)
            coEvery { checkEmailVerifiedUseCase() } returns Result.Success(false)

            val viewModel = buildViewModel()
            val before = viewModel.state.value

            viewModel.checkEmailVerified()
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `checkEmailVerified error leaves state unchanged so user can retry`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(uid = "uid-7", email = "a@b.com", isEmailVerified = false)
            coEvery { checkEmailVerifiedUseCase() } returns Result.Error(AuthError.NoNetwork)

            val viewModel = buildViewModel()
            val before = viewModel.state.value

            viewModel.checkEmailVerified()
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `checkEmailVerified with no current session is a no-op`() =
        runTest {
            // First call (init) returns a session, second call (inside checkEmailVerified) returns null
            // to simulate the session disappearing between init and the check.
            every { observeAuthState.currentSession() } returnsMany
                listOf(
                    session(uid = "uid-7", email = "a@b.com", isEmailVerified = false),
                    null,
                )

            val viewModel = buildViewModel()
            val before = viewModel.state.value

            viewModel.checkEmailVerified()
            advanceUntilIdle()

            assertEquals(before, viewModel.state.value)
            coVerify(exactly = 0) { checkEmailVerifiedUseCase() }
        }

    // ---------------------------------------------------------------------
    // signOut()
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signOut invokes use case and resets to SignedOut`() =
        runTest {
            every { observeAuthState.currentSession() } returns
                session(uid = "uid-7", isEmailVerified = true)
            coEvery { signOutUseCase() } returns Unit

            val viewModel = buildViewModel()
            assertTrue(viewModel.state.value is AuthState.Authenticated)

            viewModel.signOut()
            advanceUntilIdle()

            coVerify(exactly = 1) { signOutUseCase() }
            assertEquals(AuthState.SignedOut(), viewModel.state.value)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signOut clears any prior error or loading flags`() =
        runTest {
            every { observeAuthState.currentSession() } returns null
            coEvery { signOutUseCase() } returns Unit

            val viewModel = buildViewModel()
            viewModel.signOut()
            advanceUntilIdle()

            val state = viewModel.state.value as AuthState.SignedOut
            assertEquals(false, state.isLoading)
            assertNull(state.error)
        }
}
