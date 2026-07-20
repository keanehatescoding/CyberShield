package com.example.cybershield.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuizHistoryViewModel = hiltViewModel(),
) {
    val history = viewModel.historyPaged.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz history", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        QuizHistoryContent(
            history = history,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@Composable
private fun QuizHistoryContent(
    history: LazyPagingItems<QuizResultHistoryItem>,
    modifier: Modifier = Modifier,
) {
    when {
        // First load in flight — nothing rendered yet
        history.loadState.refresh is LoadState.Loading && history.itemCount == 0 -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    Modifier.semantics { contentDescription = "Loading quiz history" },
                )
            }
        }

        // First load failed
        history.loadState.refresh is LoadState.Error -> {
            val message =
                (history.loadState.refresh as LoadState.Error).error.message
                    ?: "Couldn't load your quiz history."
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Text(message, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { history.retry() }) { Text("Retry") }
                }
            }
        }

        // Loaded, but nothing to show
        history.itemCount == 0 -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    "No quiz attempts yet — take a quiz to see your history here.",
                    modifier = Modifier.padding(32.dp),
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = history.itemCount,
                    key = history.itemKey { it.localId },
                ) { index ->
                    history[index]?.let { entry -> QuizHistoryRow(entry) }
                }

                if (history.loadState.append is LoadState.Loading) {
                    item(key = "append-loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                if (history.loadState.append is LoadState.Error) {
                    item(key = "append-error") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = { history.retry() }) {
                                Text("Couldn't load more — tap to retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizHistoryRow(entry: QuizResultHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    when (entry.isCorrect) {
                        true -> Icons.Filled.CheckCircle
                        false -> Icons.Filled.Cancel
                        null -> Icons.Filled.CloudOff // answered offline, not graded yet
                    },
                contentDescription =
                    when (entry.isCorrect) {
                        true -> "Correct"
                        false -> "Incorrect"
                        null -> "Pending — will be graded once you're back online"
                    },
                tint =
                    when (entry.isCorrect) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = entry.moduleTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Answered: ${entry.selectedAnswer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        DateFormat
                            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(entry.answeredAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!entry.synced) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = "Not yet synced",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
