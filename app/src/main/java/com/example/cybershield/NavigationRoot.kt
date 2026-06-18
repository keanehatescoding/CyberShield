package com.example.cybershield

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cybershield.feature.auth.AuthViewModel
import com.example.cybershield.feature.auth.EmailVerificationScreen
import com.example.cybershield.feature.auth.LoginScreen
import com.example.cybershield.feature.auth.RegisterScreen
import com.example.cybershield.feature.home.HomeScreen
import com.example.cybershield.feature.leaderboard.LeaderboardScreen
import com.example.cybershield.feature.modules.ModuleDetailScreen
import com.example.cybershield.feature.profile.CertificateScreen
import com.example.cybershield.feature.profile.ProfileScreen
import com.example.cybershield.feature.quiz.QuizResult
import com.example.cybershield.feature.quiz.QuizResultScreen
import com.example.cybershield.feature.quiz.QuizScreen
import kotlinx.serialization.Serializable


@Serializable
object LoginRoute
@Serializable
object RegisterRoute
@Serializable
object EmailVerificationRoute
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
    val xpEarned: Int,  val passed: Boolean,
    val timeTaken: Long, val totalQuestions: Int,
)
@Serializable
data class CertificateRoute(val certId: String)

@Composable
fun NavigationRoot(
    deepLinkScreen: String?   = null,
    deepLinkQuizId: String?   = null,
    deepLinkModuleId: String? = null,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState     by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        when {
            authState == null -> navController.navigate(LoginRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            deepLinkScreen == "quiz"   && deepLinkQuizId   != null ->
                navController.navigate(QuizRoute(deepLinkQuizId))
            deepLinkScreen == "module" && deepLinkModuleId != null ->
                navController.navigate(ModuleRoute(deepLinkModuleId))
            else -> navController.navigate(HomeRoute) {
                popUpTo(LoginRoute) { inclusive = true }
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = if (authState != null) HomeRoute else LoginRoute,
    ) {
        composable<LoginRoute> {
            LoginScreen(onNavigateToRegister = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(
                onNavigateBack           = { navController.popBackStack() },
                onNavigateToVerification = {
                    navController.navigate(EmailVerificationRoute) {
                        popUpTo(RegisterRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<EmailVerificationRoute> {
            EmailVerificationScreen(onNavigateToLogin = {
                navController.navigate(LoginRoute) {
                    popUpTo(LoginRoute) { inclusive = true }
                }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToModule      = { navController.navigate(ModuleRoute(it)) },
                onNavigateToQuiz        = { navController.navigate(QuizRoute(it)) },
                onNavigateToLeaderboard = { navController.navigate(LeaderboardRoute) },
                onNavigateToProfile     = { navController.navigate(ProfileRoute) },
            )
        }
        composable<ModuleRoute> {
            ModuleDetailScreen(
                onNavigateBack   = { navController.popBackStack() },
                onNavigateToQuiz = { navController.navigate(QuizRoute(it)) },
            )
        }
        composable<QuizRoute> {
            QuizScreen(
                onNavigateBack     = { navController.popBackStack() },
                onNavigateToResult = { result ->
                    navController.navigate(QuizResultRoute(
                        quizId         = result.quizId,
                        score          = result.score,
                        xpEarned       = result.xpEarned,
                        passed         = result.passed,
                        timeTaken      = result.timeTaken,
                        totalQuestions = result.totalQuestions,
                    )) { popUpTo(QuizRoute::class) { inclusive = true } }
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
                onNavigateHome    = { navController.navigate(HomeRoute) {
                    popUpTo(HomeRoute) { inclusive = true } }
                },
                onRetakeQuiz      = { navController.navigate(QuizRoute(r.quizId)) {
                    popUpTo(QuizResultRoute::class) { inclusive = true } }
                },
                onViewCertificate = { navController.navigate(ProfileRoute) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onNavigateBack          = { navController.popBackStack() },
                onNavigateToCertificate = { navController.navigate(CertificateRoute(it)) },
                onSignOut               = { viewModel.signOut() },
            )
        }
        composable<CertificateRoute> { back ->
            CertificateScreen(
                certId         = back.toRoute<CertificateRoute>().certId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<LeaderboardRoute> {
            LeaderboardScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}