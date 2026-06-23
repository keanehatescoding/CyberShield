package com.example.cybershield.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EmailVerificationScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Resend cooldown — starts only after a CONFIRMED successful send ───
    var resendCooldown by remember { mutableStateOf(false) }
    LaunchedEffect(resendCooldown) {
        if (resendCooldown) {
            delay(60_000.milliseconds)
            resendCooldown = false
        }
    }

    // ★ NEW — collect events from the ViewModel, react to success/failure
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.EmailVerificationSent -> {
                    resendCooldown = true   // ★ only starts cooldown on confirmed success
                    snackbarHostState.showSnackbar("Verification email sent")
                }
                is AuthEvent.Error -> {
                    // ★ THIS is the actual fix — errors now reach the user
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },   // ★ NEW
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "📧", fontSize = 64.sp)
            Spacer(Modifier.height(24.dp))
            Text(
                text       = "Check your email",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text      = "We sent a verification link to\n${authState?.email ?: ""}",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Click the link in the email to activate your account, then come back and sign in.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // ★ NEW — addresses the spam-folder issue from earlier in the conversation
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Didn't get it? Check your spam or junk folder.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    // ★ FIXED — delegates to the ViewModel, no Compose-side Task handling
                    viewModel.resendEmailVerification()
                },
                enabled  = !resendCooldown,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (resendCooldown) "Email sent ✓" else "Resend verification email")
            }
            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick  = {
                    viewModel.signOut()
                    onNavigateToLogin()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to sign in")
            }
        }
    }
}