package com.example.cybershield.feature.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MfaVerificationScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.loginState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(0) }
    val context  = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1_000L.milliseconds)
            resendCooldown--
        }
    }

    // ★ Start cooldown automatically the moment a code is first sent —
    // otherwise the user could tap "Resend" immediately after the
    // initial send fires, defeating the point of the cooldown
    LaunchedEffect(uiState.mfaCodeSent) {
        if (uiState.mfaCodeSent && resendCooldown == 0) {
            resendCooldown = 30
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify it's you") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelMfaSignIn()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔐", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Two-factor verification",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (uiState.mfaCodeSent) "Enter the 6-digit code sent to your phone"
                else "Sending verification code...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
                label = { Text("6-digit code") },
                isError = uiState.error != null,
                supportingText = {
                    uiState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                enabled = uiState.mfaCodeSent,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { viewModel.verifyMfaCode(code) },
                enabled = code.length == 6 && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (uiState.isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else
                    Text("Verify")
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = {
                    activity?.let {viewModel.resendMfaSmsCode(it) }
                    resendCooldown = 30
                },
                enabled = resendCooldown == 0 && !uiState.isLoading,
            ) {
                Text(
                    if (resendCooldown > 0) "Resend code (${resendCooldown}s)"
                    else "Resend code"
                )
            }
        }
    }
}
