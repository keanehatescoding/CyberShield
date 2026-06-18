package com.example.cybershield.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.cybershield.R.drawable.ic_google

private enum class PasswordStrength(
    val label: String,
    val color: Color,
    val progress: Float,
) {
    WEAK  ("Weak",   Color(0xFFE53935), 0.33f),
    FAIR  ("Fair",   Color(0xFFFB8C00), 0.66f),
    STRONG("Strong", Color(0xFF43A047), 1.00f),
}

private fun evaluateStrength(password: String): PasswordStrength {
    var score = 0
    if (password.length >= 8)                          score++
    if (password.any { it.isUpperCase() })             score++
    if (password.any { it.isDigit() })                 score++
    if (password.any { !it.isLetterOrDigit() })        score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score <= 2 -> PasswordStrength.FAIR
        else       -> PasswordStrength.STRONG
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack:            () -> Unit,
    onNavigateToVerification:  () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    googleSignInHelper: GoogleSignInHelper = viewModel.googleSignInHelper
) {
    val uiState     by viewModel.registerState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Collect one-time events ────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.EmailVerificationSent -> {
                    // Navigate to the "Check your email" screen
                    onNavigateToVerification()
                }
                is AuthEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
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

            Spacer(Modifier.height(8.dp))

            // ── Header ─────────────────────────────────────────────────
            Text(
                text       = "Create your account",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = "Join CyberShield and start earning badges",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // ── Full name ──────────────────────────────────────────────
            OutlinedTextField(
                value          = uiState.name,
                onValueChange  = viewModel::onRegisterNameChange,
                label          = { Text("Full name") },
                isError        = uiState.nameError != null,
                supportingText = {
                    uiState.nameError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ── Email ──────────────────────────────────────────────────
            OutlinedTextField(
                value          = uiState.email,
                onValueChange  = viewModel::onRegisterEmailChange,
                label          = { Text("Email address") },
                isError        = uiState.emailError != null,
                supportingText = {
                    uiState.emailError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ── Password ───────────────────────────────────────────────
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value          = uiState.password,
                onValueChange  = viewModel::onRegisterPasswordChange,
                label          = { Text("Password") },
                isError        = uiState.passwordError != null,
                supportingText = {
                    uiState.passwordError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector        = if (passwordVisible)
                                Icons.Default.Visibility
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible)
                                "Hide password" else "Show password",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )

            // ── Password strength indicator ────────────────────────────
            AnimatedVisibility(visible = uiState.password.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    PasswordStrengthIndicator(password = uiState.password)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Confirm password ───────────────────────────────────────
            var confirmVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value          = uiState.confirmPassword,
                onValueChange  = viewModel::onRegisterConfirmPasswordChange,
                label          = { Text("Confirm password") },
                isError        = uiState.confirmPasswordError != null,
                supportingText = {
                    uiState.confirmPasswordError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                visualTransformation = if (confirmVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            imageVector        = if (confirmVisible)
                                Icons.Default.Visibility
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (uiState.isRegisterEnabled) {
                            viewModel.signUpWithEmail(
                                name            = uiState.name,
                                email           = uiState.email,
                                password        = uiState.password,
                                confirmPassword = uiState.confirmPassword,
                            )
                        }
                    }
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            // ── Terms notice ───────────────────────────────────────────
            Text(
                text      = "By creating an account you agree to our Terms of Service and Privacy Policy.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ── Create account button ──────────────────────────────────
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.signUpWithEmail(
                        name            = uiState.name,
                        email           = uiState.email,
                        password        = uiState.password,
                        confirmPassword = uiState.confirmPassword,
                    )
                },
                enabled  = uiState.isRegisterEnabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                AnimatedContent(
                    targetState = uiState.isLoading,
                    label       = "register button",
                ) { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            color       = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            "Create account",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            // ── Or sign up with Google ─────────────────────────────────
            DividerWithText(text = "or sign up with")

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
                                            e.message ?: "Google sign-up failed"
                                        )
                                    }
                                }
                            }
                    }
                },
                enabled  = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter            = painterResource(ic_google),
                        contentDescription = null,
                        tint               = Color.Unspecified,
                        modifier           = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google")
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Back to sign in ────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onNavigateBack) {
                    Text("Sign in", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
@Composable
fun PasswordStrengthIndicator(password: String) {
    val strength = remember(password) { evaluateStrength(password) }

    Column {
        LinearProgressIndicator(
            progress     = { strength.progress },
            modifier     = Modifier.fillMaxWidth().height(4.dp),
            color        = strength.color,
            trackColor   = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "Password strength",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = strength.label,
                style = MaterialTheme.typography.labelSmall,
                color = strength.color,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        // Show tips when weak or fair
        AnimatedVisibility(visible = strength != PasswordStrength.STRONG) {
            Text(
                text  = "Tip: use 8+ characters, a number, and a symbol",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}