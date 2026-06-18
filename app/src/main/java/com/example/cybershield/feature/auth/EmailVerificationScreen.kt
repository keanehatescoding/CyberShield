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

@Composable
fun EmailVerificationScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    // If the user verifies and re-opens the app, Firebase reloads
    // the user — navigate home automatically
    LaunchedEffect(authState) {
        val user = authState
        if (user != null && user.isEmailVerified) {
            // NavigationRoot's LaunchedEffect(authState) handles this —
            // just reload to trigger the check
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // Email icon — use your own or Material icon
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
                text      = "We sent a verification link to\n${viewModel.authState.value?.email ?: ""}",
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

            Spacer(Modifier.height(32.dp))

            // ── Resend email ───────────────────────────────────────────
            var resendCooldown by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = {
                    resendCooldown = true
                    viewModel.authState.value?.sendEmailVerification()
                },
                enabled  = !resendCooldown,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (resendCooldown) "Email sent ✓" else "Resend verification email")
            }

            Spacer(Modifier.height(12.dp))

            // ── Back to log in ──────────────────────────────────────────
            TextButton(
                onClick  = {
                    viewModel.signOut()    // sign out unverified user
                    onNavigateToLogin()     // go back to log in
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to sign in")
            }
        }
    }
}