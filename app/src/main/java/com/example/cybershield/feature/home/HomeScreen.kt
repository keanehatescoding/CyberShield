package com.example.cybershield.feature.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToModule:      (moduleId: String) -> Unit,
    onNavigateToQuiz:        (quizId: String)   -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToProfile:     () -> Unit,
    viewModel: HomeViewModel  = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.modulesError) {
        uiState.modulesError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearModulesError()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "CyberShield",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector        = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh    = viewModel::refresh,
            modifier     = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedContent(
                targetState = uiState.isLoading,
                label       = "home content",
            ) { loading ->
                if (loading) {
                    HomeLoadingSkeleton()
                } else {
                    HomeContent(
                        uiState                  = uiState,
                        greeting                 = viewModel.greeting(),
                        onNavigateToModule       = onNavigateToModule,
                        onNavigateToQuiz         = onNavigateToQuiz,
                        onNavigateToLeaderboard  = onNavigateToLeaderboard,
                    )
                }
            }
        }
    }
}

// ── Main content (shown when data is loaded) ───────────────────────────
@Composable
private fun HomeContent(
    uiState:                 HomeUiState,
    greeting:                String,
    onNavigateToModule:      (String) -> Unit,
    onNavigateToQuiz:        (String) -> Unit,
    onNavigateToLeaderboard: () -> Unit,
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {

        // ── Greeting + XP card ────────────────────────────────────────
        item(key = "header") {
            uiState.user?.let { user ->
                UserHeaderCard(
                    user     = user,
                    greeting = greeting,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // ── Badges row ────────────────────────────────────────────────
        if (uiState.user?.badges?.isNotEmpty() == true) {
            item(key = "badges") {
                BadgesSection(
                    badges   = uiState.user.badges,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Continue learning section ──────────────────────────────────
        if (uiState.pendingModules.isNotEmpty()) {
            item(key = "continue-header") {
                SectionHeader(
                    title    = "Continue learning",
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            items(
                items = uiState.pendingModules,
                key   = { it.id },
            ) { module ->
                ModuleCard(
                    module          = module,
                    isCompleted     = false,
                    onStartModule   = { onNavigateToModule(module.id) },
                    onStartQuiz     = { onNavigateToQuiz(module.quizId) },
                    modifier        = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // ── Leaderboard teaser ─────────────────────────────────────────
        item(key = "leaderboard") {
            LeaderboardTeaser(
                userXp    = uiState.user?.xp ?: 0,
                onClick   = onNavigateToLeaderboard,
                modifier  = Modifier.padding(16.dp),
            )
        }

        // ── Completed modules ──────────────────────────────────────────
        if (uiState.completedModules.isNotEmpty()) {
            item(key = "completed-header") {
                SectionHeader(
                    title    = "Completed (${uiState.completedModules.size})",
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            items(
                items = uiState.completedModules,
                key   = { it.id },
            ) { module ->
                ModuleCard(
                    module        = module,
                    isCompleted   = true,
                    onStartModule = { onNavigateToModule(module.id) },
                    onStartQuiz   = { onNavigateToQuiz(module.quizId) },
                    modifier      = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}
@Composable
fun UserHeaderCard(
    user:     User,
    greeting: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Greeting + name
            Text(
                text  = "$greeting,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text       = user.displayName.split(" ").first(),  // first name only
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.height(16.dp))

            // Level + XP label row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Level ${user.computedLevel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text  = "${user.xp} XP · ${user.xpToNextLevel} to next level",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                        .copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // XP progress bar
            LinearProgressIndicator(
                progress     = { user.xpProgress },
                modifier     = Modifier.fillMaxWidth().height(8.dp),
                color        = MaterialTheme.colorScheme.primary,
                trackColor   = MaterialTheme.colorScheme.onPrimaryContainer
                    .copy(alpha = 0.2f),
            )

            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatChip(label = "Quizzes",  value = "${user.completedQuizzes.size}")
                StatChip(label = "Modules",  value = "${user.completedModules.size}")
                StatChip(label = "Badges",   value = "${user.badges.size}")
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}
@Composable
fun ModuleCard(
    module: Module,
    isCompleted:   Boolean,
    onStartModule: () -> Unit,
    onStartQuiz:   () -> Unit,
    modifier:      Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface,
        ),
        border = if (isCompleted) null
        else CardDefaults.outlinedCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // New badge
                    if (module.new) {
                        Badge { Text("NEW") }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text       = module.title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Completed checkmark
                if (isCompleted) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Meta row — duration + XP reward
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(
                    onClick = {},
                    label   = { Text("${module.durationMins} min") },
                    leadingIcon = {
                        Icon(Icons.Default.PlayCircle, null,
                            Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = {},
                    label   = { Text("+${module.xpReward} XP") },
                    leadingIcon = {
                        Icon(Icons.Default.Star, null,
                            Modifier.size(16.dp))
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = onStartModule,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isCompleted) "Rewatch" else "Watch")
                }
                Button(
                    onClick  = onStartQuiz,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Quiz, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isCompleted) "Retake quiz" else "Take quiz")
                }
            }
        }
    }
}
@Composable
fun BadgesSection(badges: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionHeader(title = "Your badges")
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(badges, key = { it }) { badge ->
                ElevatedCard(modifier = Modifier.size(72.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = badgeEmoji(badge), fontSize = 28.sp)
                            Text(
                                text  = badge,
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

@Composable
fun LeaderboardTeaser(
    userXp:   Int,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "🏆", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Leaderboard",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "You have $userXp XP · See how you rank",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier   = modifier,
    )
}

// Loading skeleton — shown while data is fetching
@Composable
fun HomeLoadingSkeleton() {
    Column(
        modifier            = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Card(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {}
        }
    }
}

// Maps badge names to emoji
fun badgeEmoji(badge: String): String = when (badge) {
    "CyberDefender"   -> "🛡"
    "PhishingExpert"  -> "🎣"
    "PasswordPro"     -> "🔑"
    "NetworkNinja"    -> "🌐"
    "MalwareHunter"   -> "🦠"
    "FirstLogin"      -> "⭐"
    else              -> "🏅"
}
