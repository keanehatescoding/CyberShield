package com.example.cybershield.feature.leaderboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏆 Leaderboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Top 3 podium
                    item(key = "podium") {
                        if (uiState.entries.size >= 3) {
                            PodiumRow(top3 = uiState.entries.take(3))
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // Full ranked list
                    itemsIndexed(
                        items = uiState.entries,
                        key   = { _, entry -> entry.uid },
                    ) { index, entry ->
                        LeaderboardRow(
                            rank      = index + 1,
                            entry     = entry,
                            isCurrentUser = entry.uid == uiState.currentUid,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank:          Int,
    entry:         LeaderboardEntry,
    isCurrentUser: Boolean,
) {
    val rankEmoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
        border = if (isCurrentUser)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null,
    ) {
        Row(
            modifier          = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(rankEmoji, fontSize = if (rank <= 3) 24.sp else 16.sp,
                modifier = Modifier.width(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isCurrentUser) "${entry.displayName} (You)" else entry.displayName,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    "Level ${entry.level} · ${entry.badges.size} badges",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${entry.xp} XP",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PodiumRow(top3: List<LeaderboardEntry>) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.Bottom,
    ) {
        PodiumEntry(entry = top3[1], rank = 2, height = 80.dp)
        PodiumEntry(entry = top3[0], rank = 1, height = 110.dp)
        PodiumEntry(entry = top3[2], rank = 3, height = 60.dp)
    }
}

@Composable
private fun PodiumEntry(
    entry:  LeaderboardEntry,
    rank:   Int,
    height: androidx.compose.ui.unit.Dp,
) {
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(medal, fontSize = 28.sp)
        Text(
            entry.displayName.split(" ").first(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${entry.xp} XP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.width(80.dp).height(height),
        ) {}
    }
}