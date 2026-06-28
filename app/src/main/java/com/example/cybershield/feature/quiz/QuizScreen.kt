package com.example.cybershield.feature.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.ui.theme.LoadingScreen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onNavigateBack:       () -> Unit,
    onNavigateToResult:   (QuizResult) -> Unit,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate to result screen when quiz completes
    LaunchedEffect(uiState) {
        if (uiState is QuizUiState.Completed) {
            onNavigateToResult((uiState as QuizUiState.Completed).result)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState is QuizUiState.Active) {
                        val active = uiState as QuizUiState.Active
                        LinearProgressIndicator(
                            progress   = { active.progress },
                            modifier   = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { innerPadding ->

        AnimatedContent(
            targetState = uiState,
            label       = "quiz state",
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            modifier = Modifier.padding(innerPadding),
        ) { state ->
            when (state) {
                is QuizUiState.Loading -> LoadingScreen(message="Loading quiz")
                is QuizUiState.Active  -> QuizActiveScreen(
                    state     = state,
                    onSelect  = viewModel::selectAnswer,
                )
                is QuizUiState.Error  -> QuizErrorScreen(
                    message   = state.message,
                    onRetry   = onNavigateBack,
                )
                is QuizUiState.Completed -> LoadingScreen()
            }
        }
    }
}

// ── Active question screen ─────────────────────────────────────────────
@Composable
private fun QuizActiveScreen(
    state:    QuizUiState.Active,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Timer + score row ──────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            TimerDisplay(
                timeLeft      = state.timeLeft,
                timerProgress = state.timerProgress,
            )
            ScoreDisplay(score = state.score)
        }

        // ── Question counter ───────────────────────────────────────────
        Text(
            text  = "Question ${state.questionIndex + 1} of ${state.totalQuestions}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Question text ──────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(
                text       = state.question.text,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(20.dp),
                lineHeight = 26.sp,
            )
        }

        // ── Answer options ─────────────────────────────────────────────
        state.question.options.forEachIndexed { index, option ->
            AnswerOptionButton(
                text          = option,
                index         = index,
                isSelected    = state.selectedOption == index,
                isAnswered    = state.isAnswered,
                isCorrect     = index == state.question.correctIndex,
                onClick       = { onSelect(index) },
            )
        }

        // ── Explanation (shown after answering) ────────────────────────
        AnimatedVisibility(visible = state.isAnswered && state.question.explanation.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text       = if (state.isCorrect == true) "✓ Correct!" else "✗ Not quite",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = if (state.isCorrect == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = state.question.explanation,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ── Answer button with correct/wrong highlight ─────────────────────────
@Composable
private fun AnswerOptionButton(
    text:       String,
    index:      Int,
    isSelected: Boolean,
    isAnswered: Boolean,
    isCorrect:  Boolean,
    onClick:    () -> Unit,
) {
    val containerColor = when {
        !isAnswered                   -> MaterialTheme.colorScheme.surface
        isCorrect                     -> Color(0xFF4CAF50)  // green
                isSelected && !isCorrect      -> Color(0xFFF44336)  // red
            else                          -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isAnswered && (isCorrect || isSelected) -> Color.White
        else                                    -> MaterialTheme.colorScheme.onSurface
    }
    val letters = listOf("A", "B", "C", "D")

    Surface(
        onClick        = onClick,
        enabled        = !isAnswered,
        shape          = RoundedCornerShape(12.dp),
        color          = animateColorAsState(containerColor, label = "option color").value,
        border         = if (!isAnswered)
            ButtonDefaults.outlinedButtonBorder(true)
        else
            null,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = contentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text       = letters.getOrElse(index) { "" },
                        style      = MaterialTheme.typography.labelMedium,
                        color      = contentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text  = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

// ── Timer display — isolated so only it recomposes every second ────────
@Composable
private fun TimerDisplay(timeLeft: Int, timerProgress: Float) {
    val timerColor = when {
        timeLeft > 20 -> MaterialTheme.colorScheme.primary
        timeLeft > 10 -> Color(0xFFFB8C00)   // amber
            else          -> MaterialTheme.colorScheme.error
    }
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress    = { timerProgress },
            modifier    = Modifier.size(52.dp),
            color       = animateColorAsState(timerColor, label = "timer color").value,
            strokeWidth = 4.dp,
        )
        Text(
            text       = "$timeLeft",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = timerColor,
        )
    }
}

@Composable
private fun ScoreDisplay(score: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text  = "Score",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text       = "$score",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun QuizErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("😔", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Go back") }
        }
    }
}