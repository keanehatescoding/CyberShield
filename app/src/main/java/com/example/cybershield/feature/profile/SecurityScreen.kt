package com.example.cybershield.feature.profile

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cybershield.feature.auth.AuthEvent
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: MfaEnrollmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    var showDisableConfirm by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1_000L.milliseconds)
            resendCooldown--
        }
    }

    LaunchedEffect(uiState.isCodeSent) {
        if (uiState.isCodeSent && resendCooldown == 0) {
            resendCooldown = 30
        } else if (!uiState.isCodeSent) {
            resendCooldown = 0
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.MfaEnrollmentSuccess ->
                    snackbarHostState.showSnackbar("Two-factor authentication enabled")
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
        ) {
            // ── Header card explaining 2FA ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Shield, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Two-factor authentication",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Add an extra layer of protection by requiring a code from your phone when signing in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = when {
                    uiState.isEnrolled -> "enrolled"
                    uiState.isCodeSent -> "confirm_code"
                    else               -> "enter_phone"
                },
                label = "security step",
            ) { step ->
                when (step) {

                    // ── Already enrolled — show status + disable option ─────────
                    "enrolled" -> Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Two-factor authentication is on",
                                fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick  = { showDisableConfirm = true },
                            enabled  = !uiState.isLoading,
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Turn off two-factor authentication") }
                    }

                    // ── Step 2: enter the 6-digit code ───────────────────────────
                    "confirm_code" -> Column {
                        Text("Enter the 6-digit code sent to ${uiState.phoneNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value          = uiState.smsCode,
                            onValueChange  = { if (it.length <= 6) viewModel.onSmsCodeChanged(it.filter { c -> c.isDigit() }) },
                            label          = { Text("6-digit code") },
                            isError        = uiState.error != null,
                            supportingText = { uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine     = true,
                            modifier       = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick  = viewModel::confirmEnrollment,
                            enabled  = uiState.canConfirmCode,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (uiState.isLoading)
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Text("Verify and enable")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = viewModel::resetEnrollmentFlow) {
                                Text("Change number")
                            }
                            // ★ UPDATED — now has cooldown, matching MfaVerificationScreen
                            TextButton(
                                onClick = {
                                    activity?.let { viewModel.resendCode(it) }
                                    resendCooldown = 30
                                },
                                enabled = resendCooldown == 0 && !uiState.isLoading,
                            ) {
                                Text(
                                    if (resendCooldown > 0) "Resend (${resendCooldown}s)"
                                    else "Resend code"
                                )
                            }
                        }
                    }

                    // ── Step 1: enter phone number ───────────────────────────────
                    else -> Column {
                        OutlinedTextField(
                            value          = uiState.phoneNumber,
                            onValueChange  = viewModel::onPhoneNumberChanged,
                            label          = { Text("Phone number") },
                            placeholder    = { Text("+254712345678") },
                            isError        = uiState.error != null,
                            supportingText = {
                                if (uiState.error != null)
                                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                                else
                                    Text("Include your country code")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine     = true,
                            modifier       = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick  = { activity?.let { viewModel.sendEnrollmentCode(it) } },
                            enabled  = uiState.canSendCode && activity != null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (uiState.isLoading)
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Text("Send code")
                        }
                    }
                }
            }
        }
    }

    // ── Disable confirmation dialog ──────────────────────────────────────
    if (showDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableConfirm = false },
            title   = { Text("Turn off two-factor authentication?") },
            text    = { Text("Your account will be less secure. You'll only need your password to sign in.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.unenroll(); showDisableConfirm = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Turn off") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirm = false }) { Text("Cancel") }
            },
        )
    }
}