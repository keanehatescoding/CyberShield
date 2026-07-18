package com.example.cybershield.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cybershield.feature.home.badgeEmoji

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCertificate: (certId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Errors ─────────────────────────────────────────────────────
            uiState.profileError?.let { message ->
                item(key = "profile-error") {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            uiState.certificatesError?.let { message ->
                item(key = "certificates-error") {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ── Profile card ───────────────────────────────────────────────
            item(key = "profile-card") {
                uiState.user?.let { user ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Avatar
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(80.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text =
                                            user.displayName
                                                .firstOrNull()
                                                ?.uppercaseChar()
                                                ?.toString() ?: "?",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                user.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))

                            // XP progress
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Level ${user.computedLevel}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    "${user.xp} XP total",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { user.xpProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                        }
                    }
                }
            }

            // ── Stats row ──────────────────────────────────────────────────
            item(key = "stats") {
                uiState.user?.let { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        listOf(
                            Triple("🏆", "${user.completedQuizzes.size}", "Quizzes"),
                            Triple("📚", "${user.completedModules.size}", "Modules"),
                            Triple("🎖", "${user.badges.size}", "Badges"),
                        ).forEach { (emoji, value, label) ->
                            Card(modifier = Modifier.weight(1f)) {
                                Column(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(emoji, fontSize = 24.sp)
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Badges ─────────────────────────────────────────────────────
            uiState.user?.let { user ->
                if (user.badges.isNotEmpty()) {
                    item(key = "badges-header") {
                        Text(
                            "Earned badges",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                item(key = "badges-row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(user.badges, key = { it }) { badge ->
                            ElevatedCard(modifier = Modifier.size(72.dp)) {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(badgeEmoji(badge), fontSize = 28.sp)
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Certificates ───────────────────────────────────────────────
            if (uiState.certificates.isNotEmpty()) {
                item(key = "certs-header") {
                    Text(
                        "My certificates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(uiState.certificates, key = { it.id }) { cert ->
                    OutlinedCard(
                        onClick = { onNavigateToCertificate(cert.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🎓", fontSize = 32.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    cert.quizTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Score: ${cert.score} · ${cert.datePassed.let {
                                        java.text
                                            .SimpleDateFormat(
                                                "dd MMM yyyy",
                                                java.util.Locale.getDefault(),
                                            ).format(it)
                                    } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }

            // ── Quiz history ──────────────────────────────────────────────
            item(key = "quiz-history-entry") {
                OutlinedCard(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📜", fontSize = 32.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Quiz history",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "See every past answer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }

            // ── Sign out ───────────────────────────────────────────────────
            item(key = "signout") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}
