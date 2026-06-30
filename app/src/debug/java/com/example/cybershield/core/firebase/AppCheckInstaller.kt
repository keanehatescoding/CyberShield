package com.example.cybershield.core.firebase

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug build type: installs the Debug App Check provider, which bypasses
 * device attestation for local development. This type only exists in the
 * `debug` source set, so it (and its `firebase-appcheck-debug` dependency)
 * never ships in a release build.
 */
object AppCheckInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
