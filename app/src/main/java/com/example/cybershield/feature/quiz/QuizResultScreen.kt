package com.example.cybershield.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun QuizResultScreen(
    onNavigateHome: () -> Unit,
    onRetakeQuiz: (String) -> Unit,
    onViewCertificate: (String) -> Unit,
    viewModel: QuizResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        QuizResultUiState.Loading -> {
            LoadingScreen()
        }

        is QuizResultUiState.Loaded -> {
            QuizResultContent(
                result = state.result,
                onNavigateHome = onNavigateHome,
                onRetakeQuiz = {
                    onRetakeQuiz(state.result.quizId)
                },
                onViewCertificate = {
                    onViewCertificate(viewModel.resultId)
                },
            )
        }

        QuizResultUiState.NotFound -> {
            QuizErrorScreen(
                message = "Quiz result not found.",
                onRetry = onNavigateHome,
            )
        }
    }
}

@Composable
private fun QuizResultContent(
    result: QuizResult,
    onNavigateHome: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onViewCertificate: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Pass / fail emoji ──────────────────────────────────────
            Text(
                text = if (result.passed) "🏆" else "💪",
                fontSize = 72.sp,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (result.passed) "Quiz passed!" else "Keep practising!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text =
                    if (result.provisional) {
                        "Some answers are still offline — this score, along with any XP and certificate, will update once they sync."
                    } else if (result.passed) {
                        "You earned +${result.xpEarned} XP and unlocked a certificate!"
                    } else {
                        "You earned +${result.xpEarned} XP. Try again to pass and get a certificate."
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // ── Stats card ─────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ResultStat(label = "Score", value = "${result.correctCount}/${result.totalQuestions}")
                    ResultStat(label = "Accuracy", value = "${result.percentage}%")
                    ResultStat(label = "XP earned", value = "+${result.xpEarned}")
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Certificate button (only if passed and finalized) ───────
            if (result.passed && !result.provisional) {
                Button(
                    onClick = onViewCertificate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("🎓 View my certificate")
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Retake / Home ──────────────────────────────────────────
            OutlinedButton(
                onClick = onRetakeQuiz,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (result.passed) "Retake quiz" else "Try again") }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onNavigateHome, modifier = Modifier.fillMaxWidth()) {
                Text("Back to home")
            }
        }
    }
}

@Composable
private fun ResultStat(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
