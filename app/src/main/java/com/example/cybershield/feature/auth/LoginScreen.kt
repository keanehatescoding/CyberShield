package com.example.cybershield.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.cybershield.R
import com.example.cybershield.ui.theme.CyberShieldTheme

@Composable
private fun LoginHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter            = painterResource(R.drawable.ic_google),
            contentDescription = "CyberShield",
            modifier           = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "Welcome back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = "Sign in to continue your cybersecurity journey",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
@Composable
internal fun DividerWithText(text: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text     = text,
            modifier = Modifier.padding(horizontal = 12.dp),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel<AuthViewModel>(),
    googleSignInHelper: GoogleSignInHelper = viewModel.googleSignInHelper
) {
    val uiState by viewModel.loginState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCollision by remember { mutableStateOf<AuthEvent.AccountCollision?>(null) }
    var linkPassword by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    // Collect one-time events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.AccountCollision -> {
                    pendingCollision = event   // triggers the dialog below
                }
                is AuthEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Spacer(Modifier.height(56.dp))

            // ── Logo + title ───────────────────────────────────────────
            LoginHeader()
            Spacer(Modifier.height(40.dp))
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email address") },
                isError = uiState.emailError != null,
                supportingText = {
                    uiState.emailError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(
                            FocusDirection.Down
                        )
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                isError = uiState.passwordError != null,
                supportingText = {
                    uiState.passwordError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            passwordVisible = !passwordVisible
                        }
                    ) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Default.Visibility
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible)
                                "Hide password" else "Show Password"

                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (uiState.isSignInEnabled) {
                            viewModel.signInWithEmail(uiState.email, uiState.password)
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        resetEmail = uiState.email
                        showResetDialog = true
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("Forgot password?")
                }
            }
            Spacer(Modifier.padding(16.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.signInWithEmail(uiState.email, uiState.password)
                },
                enabled = uiState.isSignInEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sign in", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(20.dp))
            DividerWithText(
                text = "or continue with"
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        googleSignInHelper
                            .signIn(activityContext = context)
                            .onSuccess { idToken ->
                                viewModel.signInWithGoogle(idToken)
                            }
                            .onFailure { e ->
                                if (e.message != "Sign-in cancelled") {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            e.message ?: "Google sign-in Failed"
                                        )
                                    }
                                }
                            }
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_google),
                        contentDescription = null,
                        tint               = Color.Unspecified,
                        modifier           = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google")
                }
            }
            Spacer(Modifier.height(32.dp))

            // ── Navigate to register ───────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onNavigateToRegister) {
                    Text("Sign up", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // ── Forgot password dialog ─────────────────────────────────────
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title   = { Text("Reset your password") },
                text    = {
                    Column {
                        Text("Enter your email and we'll send you a reset link.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value         = resetEmail,
                            onValueChange = { resetEmail = it },
                            label         = { Text("Email address") },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick  = {
                            viewModel.sendPasswordReset(resetEmail)
                            showResetDialog = false
                        },
                        enabled = resetEmail.isNotBlank(),
                    ) { Text("Send reset email") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        // Account linking dialog — shown when pendingCollision is non-null
        pendingCollision?.let { collision ->
            AlertDialog(
                onDismissRequest = { pendingCollision = null; linkPassword = "" },
                title   = { Text("Link your accounts") },
                text    = {
                    Column {
                        Text(
                            "${collision.email} is already registered with email/password. " +
                                    "Enter your password to link your Google account."
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value          = linkPassword,
                            onValueChange  = { linkPassword = it },
                            label          = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine     = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.linkGoogleToExistingAccount(
                                email            = collision.email,
                                password         = linkPassword,
                                googleCredential = collision.googleCredential,
                            )
                            pendingCollision = null
                            linkPassword     = ""
                        },
                        enabled = linkPassword.length >= 6,
                    ) { Text("Link accounts") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCollision = null; linkPassword = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
@Preview(showBackground=true, name="Login - empty")
@Composable
private fun LoginScreenPreview(){
    CyberShieldTheme {
        LoginScreen(onNavigateToRegister = {})
    }
}
@Preview(showBackground=true, name= "Login - loading")
@Composable
private fun LoginScreenLoadingPreview(){
    CyberShieldTheme {
    }
}