package com.example.cybershield

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Minimal activity used by @HiltAndroidTest instrumented tests.
 * ComposeTestRule needs a concrete Activity to launch — this one satisfies
 * Hilt's @AndroidEntryPoint requirement with no production UI of its own.
 *
 * Must be declared in androidTest/AndroidManifest.xml so the test runner
 * can resolve it at runtime.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()