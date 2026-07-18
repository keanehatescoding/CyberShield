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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EmailVerificationScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state as? AuthState.AwaitingEmailVerification ?: return

    LaunchedEffect(s.email) {
        while (isActive) {
            delay(3_000L.milliseconds)
            viewModel.checkEmailVerified()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("📧", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(16.dp))
            Text("Check your email", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("We sent a verification link to\n${s.email}", textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "Didn't get it? Check your spam folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = viewModel::resendVerificationEmail,
                enabled = !s.isResending && s.resendCooldownSeconds == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (s.resendCooldownSeconds > 0) "Resend (${s.resendCooldownSeconds}s)" else "Resend email")
            }
            s.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
