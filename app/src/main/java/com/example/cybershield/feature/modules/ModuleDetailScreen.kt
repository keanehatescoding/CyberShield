package com.example.cybershield.feature.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToQuiz: (quizId: String) -> Unit,
    viewModel: ModuleViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedPosition by viewModel.savedPositionMs.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val isSavedPositionLoaded by viewModel.isSavedPositionLoaded.collectAsStateWithLifecycle()
    var showSpeedMenu by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.loadModule()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.module?.title ?: "",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Playback speed selector
                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text("${playbackSpeed}x")
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        viewModel.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    },
                                    trailingIcon = {
                                        if (speed == playbackSpeed) {
                                            Icon(Icons.Default.Check, null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val module = uiState.module

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.error ?: "Something went wrong",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            module != null -> {

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (uiState.isStale) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Showing saved content — couldn't refresh",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    if (isSavedPositionLoaded) {
                        // ── Video player ───────────────────────────────────────
                        VideoPlayerComposable(
                            videoUrl = module.videoUrl,
                            savedPosition = savedPosition,
                            playbackSpeed = playbackSpeed,
                            onVideoEnded = { viewModel.onVideoCompleted() },
                            onPositionChanged = { pos -> viewModel.savePosition(pos) },
                            onPlaybackError = { message -> videoError = message },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                        )
                        if (videoError != null) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Couldn't play this video — check your connection and reopen the module.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        // ── Module info ────────────────────────────────────
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(module.category) },
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${module.durationMins} min") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Schedule,
                                        null,
                                        Modifier.size(14.dp),
                                    )
                                },
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("+${module.xpReward} XP") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        null,
                                        Modifier.size(14.dp),
                                    )
                                },
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(24.dp))

                        HorizontalDivider()

                        Spacer(Modifier.height(24.dp))

                        // ── Take quiz CTA ──────────────────────────────────
                        Text(
                            text = "Test your knowledge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Complete the quiz to earn your XP and unlock a certificate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigateToQuiz(module.quizId) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Default.Quiz, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (uiState.isAlreadyCompleted) {
                                    "Retake quiz"
                                } else {
                                    "Take the quiz · ${module.xpReward} XP"
                                },
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }

                // ── Module completion dialog ───────────────────────────────
                if (uiState.showCompletionDialog) {
                    AlertDialog(
                        onDismissRequest = viewModel::onCompletionDialogDismissed,
                        icon = { Text("🎉", style = MaterialTheme.typography.displaySmall) },
                        title = { Text("Lesson complete!") },
                        text = {
                            Text(
                                "You've earned +${module.xpReward} XP. " +
                                    "Take the quiz to unlock your certificate!",
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.onCompletionDialogDismissed()
                                onNavigateToQuiz(module.quizId)
                            }) { Text("Take quiz now") }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::onCompletionDialogDismissed) {
                                Text("Later")
                            }
                        },
                    )
                }
            }
        }
    }
}
