package com.example.cybershield

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.ui.ConnectivityViewModel
import com.example.cybershield.feature.auth.AuthState
import com.example.cybershield.feature.auth.AuthViewModel
import com.example.cybershield.feature.auth.EmailVerificationScreen
import com.example.cybershield.feature.auth.RegisterAndSignInScreen
import com.example.cybershield.feature.home.HomeScreen
import com.example.cybershield.feature.leaderboard.LeaderboardScreen
import com.example.cybershield.feature.modules.ModuleDetailScreen
import com.example.cybershield.feature.profile.CertificateScreen
import com.example.cybershield.feature.profile.ProfileScreen
import com.example.cybershield.feature.quiz.QuizResultScreen
import com.example.cybershield.feature.quiz.QuizScreen
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
data class ModuleRoute(val moduleId: String)

@Serializable
data class QuizRoute(val quizId: String)

@Serializable
data class QuizResultRoute(
    val quizId: String, val score: Int,
    val xpEarned: Int, val passed: Boolean,
    val timeTaken: Long, val totalQuestions: Int,
)

@Serializable
data class CertificateRoute(val certId: String)

@Composable
fun NavigationRoot(
    deepLinkScreen: String? = null,
    deepLinkQuizId: String? = null,
    deepLinkModuleId: String? = null,
) {

    val authViewModel: AuthViewModel = hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)
    val connectivityViewModel: ConnectivityViewModel =
        hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val isOnline by connectivityViewModel.isOnline.collectAsStateWithLifecycle()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        OfflineBanner(isOffline = !isOnline)
        when (authState) {
            is AuthState.Resolving -> LoadingScreen()
            is AuthState.SignedOut -> RegisterAndSignInScreen(authViewModel)
            is AuthState.AwaitingEmailVerification -> EmailVerificationScreen(authViewModel)
            is AuthState.Authenticated -> {

                val navController = rememberNavController()
                LaunchedEffect(deepLinkScreen, deepLinkQuizId, deepLinkModuleId) {
                    when (deepLinkScreen) {
                        "quiz" if deepLinkQuizId != null ->
                            navController.navigate(QuizRoute(deepLinkQuizId)) {
                                launchSingleTop = true
                            }

                        "module" if deepLinkModuleId != null ->
                            navController.navigate(ModuleRoute(deepLinkModuleId)) {
                                launchSingleTop = true
                            }

                        else -> {
                        }
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
                    composable<ModuleRoute> {
                        ModuleDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToQuiz = { navController.navigate(QuizRoute(it)) },
                        )
                    }
                    composable<QuizRoute> {
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
                                    ),
                                ) { popUpTo(QuizRoute::class) { inclusive = true } }
                            },
                        )
                    }
                    composable<QuizResultRoute> { back ->
                        val r = back.toRoute<QuizResultRoute>()
                        QuizResultScreen(
                            result = QuizResult(
                                r.quizId, r.score, r.totalQuestions,
                                r.xpEarned, r.passed, r.timeTaken
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
                }
            }
        }
    }
}