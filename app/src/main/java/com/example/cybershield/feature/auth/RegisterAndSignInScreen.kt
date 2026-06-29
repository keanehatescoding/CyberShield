package com.example.cybershield.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RegisterAndSignInScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedOut = state as? AuthState.SignedOut ?: return

    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var hasAttemptedSubmit by rememberSaveable { mutableStateOf(false) }

    val emailError: String? =
        when {
            !hasAttemptedSubmit -> null
            email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches()
            -> "Enter a valid email address"
            else -> null
        }
    val passwordError: String? =
        when {
            !hasAttemptedSubmit -> null
            password.isBlank() -> "Password is required"
            isRegisterMode && password.length < 8
            -> "Password must be at least 8 characters"
            else -> null
        }
    val nameError: String? =
        when {
            !isRegisterMode || !hasAttemptedSubmit -> null
            name.isBlank() -> "Name is required"
            else -> null
        }

    val isFormValid =
        emailError == null &&
            passwordError == null &&
            nameError == null &&
            email.isNotBlank() &&
            android.util.Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches() &&
            password.length >= (if (isRegisterMode) 8 else 1) &&
            (!isRegisterMode || name.isNotBlank())

    // NOTE: no local Scaffold here. MainActivity's outer Scaffold already
    // reserves space for system bars + IME via its default contentWindowInsets,
    // and a second nested Scaffold double-counts that inset, collapsing this
    // Column's available height to zero when the keyboard opens (the "black
    // screen" bug). This screen just fills the space it's given.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("🛡", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            if (isRegisterMode) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(32.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            singleLine = true,
            isError = emailError != null,
            supportingText = { emailError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        var passwordVisible by rememberSaveable { mutableStateOf(false) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            isError = passwordError != null,
            supportingText = { passwordError?.let { Text(it) } },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        signedOut.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                hasAttemptedSubmit = true
                if (!isFormValid) return@Button

                if (isRegisterMode) {
                    viewModel.register(name, email, password)
                } else {
                    viewModel.signIn(email, password)
                }
            },
            enabled = !signedOut.isLoading && isFormValid,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (signedOut.isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isRegisterMode) "Create account" else "Sign in")
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                isRegisterMode = !isRegisterMode
                hasAttemptedSubmit = false
            },
            enabled = !signedOut.isLoading,
        ) {
            Text(if (isRegisterMode) "Already have an account? Sign in" else "New here? Create an account")
        }
    }
}
