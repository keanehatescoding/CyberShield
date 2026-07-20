package com.example.cybershield.core.firebase

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for CyberShieldMessagingService.deepLinkUri.
 *
 * Notification taps used to attach quizId/moduleId as plain Intent extras,
 * which nothing in the app ever read — NavigationRoot only routes via
 * navController.handleDeepLink(intent), which matches on intent.data, not
 * extras. Every notification tap silently opened Home regardless of type.
 * These tests pin the Uri shape (see NavigationRoot.kt's moduleDeepLinks /
 * quizDeepLinks: "cybershield://module/{moduleId}", "cybershield://quiz/{quizId}")
 * so that regression can't reappear unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CyberShieldMessagingServiceTest {
    @Test
    fun `deepLinkUri builds a quiz deep link matching the registered nav pattern`() {
        val uri = CyberShieldMessagingService.deepLinkUri("quiz", "quiz_123")

        assertEquals("cybershield://quiz/quiz_123", uri.toString())
    }

    @Test
    fun `deepLinkUri builds a module deep link matching the registered nav pattern`() {
        val uri = CyberShieldMessagingService.deepLinkUri("module", "module_abc")

        assertEquals("cybershield://module/module_abc", uri.toString())
    }

    @Test
    fun `deepLinkUri percent-encodes ids so a slash in server data can't break the route`() {
        val uri = CyberShieldMessagingService.deepLinkUri("quiz", "not/a-plain-id")

        // If "/" weren't encoded, this would parse as two extra path
        // segments instead of a single {quizId} argument, and the nav
        // graph's deep link pattern would fail to match at all.
        assertEquals("cybershield://quiz/not%2Fa-plain-id", uri.toString())
        assertEquals(listOf("not/a-plain-id"), uri.pathSegments)
    }
}
