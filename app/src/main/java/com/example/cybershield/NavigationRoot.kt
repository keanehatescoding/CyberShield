package com.example.cybershield

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.ui.ConnectivityViewModel
import com.example.cybershield.feature.auth.AuthState
import com.example.cybershield.feature.auth.AuthViewModel
import com.example.cybershield.feature.auth.EmailVerificationScreen
import com.example.cybershield.feature.auth.RegisterAndSignInScreen
import com.example.cybershield.feature.history.QuizHistoryScreen
import com.example.cybershield.feature.home.HomeScreen
import com.example.cybershield.feature.leaderboard.LeaderboardScreen
import com.example.cybershield.feature.modules.ModuleDetailScreen
import com.example.cybershield.feature.profile.CertificateScreen
import com.example.cybershield.feature.profile.ProfileScreen
import com.example.cybershield.feature.quiz.QuizResultScreen
import com.example.cybershield.feature.quiz.QuizScreen
import com.example.cybershield.navigation.DeepLinkViewModel
import com.example.cybershield.ui.theme.LoadingScreen
import com.example.cybershield.ui.theme.OfflineBanner
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ProfileRoute

@Serializable
object LeaderboardRoute

@Serializable
object QuizHistoryRoute

@Serializable
data class ModuleRoute(
    val moduleId: String,
)

val moduleDeepLinks = listOf(
    navDeepLink<ModuleRoute>(basePath = "cybershield://module"),
)
@Serializable
data class QuizRoute(
    val quizId: String,
)
val quizDeepLinks = listOf(
    navDeepLink<QuizRoute>(basePath = "cybershield://quiz"),
)

@Serializable
data class QuizResultRoute(
    val resultId: String
)
@Serializable
data class CertificateRoute(
    val certId: String,
)

@Composable
fun NavigationRoot(
    deepLinkViewModel: DeepLinkViewModel = hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity),

    ) {
    val authViewModel: AuthViewModel = hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)
    val connectivityViewModel: ConnectivityViewModel =
        hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val isOnline by connectivityViewModel.isOnline.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
    ) {
        OfflineBanner(isOffline = !isOnline)
        when (authState) {
            is AuthState.Resolving -> LoadingScreen()
            is AuthState.SignedOut -> RegisterAndSignInScreen(authViewModel)
            is AuthState.AwaitingEmailVerification -> EmailVerificationScreen(authViewModel)
            is AuthState.Authenticated -> {
                val navController = rememberNavController()
                val pendingIntent by deepLinkViewModel.pendingIntent.collectAsStateWithLifecycle()
                LaunchedEffect(pendingIntent) {
                    pendingIntent?.let { intent ->
                        navController.handleDeepLink(intent)
                        deepLinkViewModel.consumed()
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            onNavigateToModule = { navController.navigate(ModuleRoute(it)) },
                            onNavigateToQuiz = { navController.navigate(QuizRoute(it)) },
                            onNavigateToLeaderboard = { navController.navigate(LeaderboardRoute) },
                            onNavigateToProfile = { navController.navigate(ProfileRoute) },
                        )
                    }
                    composable<ModuleRoute>(deepLinks = moduleDeepLinks) {
                        ModuleDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToQuiz = { navController.navigate(QuizRoute(it)) },
                        )
                    }
                    composable<QuizRoute>(deepLinks = quizDeepLinks) {
                        QuizScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToResult = { result ->
                                navController.navigate(
                                    QuizResultRoute(
                                        quizId = result.quizId,
                                        score = result.score,
                                        xpEarned = result.xpEarned,
                                        passed = result.passed,
                                        timeTaken = result.timeTaken,
                                        totalQuestions = result.totalQuestions,
                                        correctCount = result.correctCount,
                                        percentage = result.percentage,
                                    ),
                                ) { popUpTo(QuizRoute::class) { inclusive = true } }
                            },
                        )
                    }
                    composable<QuizResultRoute> { back ->
                        val r = back.toRoute<QuizResultRoute>()
                        QuizResultScreen(
                            result =
                                QuizResult(
                                    quizId = r.quizId,
                                    score = r.score,
                                    totalQuestions = r.totalQuestions,
                                    correctCount = r.correctCount,
                                    percentage = r.percentage,
                                    xpEarned = r.xpEarned,
                                    passed = r.passed,
                                    timeTaken = r.timeTaken,
                                ),
                            onNavigateHome = {
                                navController.navigate(HomeRoute) {
                                    popUpTo(HomeRoute) { inclusive = true }
                                }
                            },
                            onRetakeQuiz = {
                                navController.navigate(QuizRoute(r.quizId)) {
                                    popUpTo(QuizResultRoute::class) { inclusive = true }
                                }
                            },
                            onViewCertificate = { navController.navigate(ProfileRoute) },
                        )
                    }
                    composable<ProfileRoute> {
                        ProfileScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToCertificate = { navController.navigate(CertificateRoute(it)) },
                            onNavigateToHistory = { navController.navigate(QuizHistoryRoute) },
                            onSignOut = { authViewModel.signOut() },
                        )
                    }
                    composable<CertificateRoute> { back ->
                        CertificateScreen(
                            certId = back.toRoute<CertificateRoute>().certId,
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }
                    composable<LeaderboardRoute> {
                        LeaderboardScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable<QuizHistoryRoute> {
                        QuizHistoryScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
