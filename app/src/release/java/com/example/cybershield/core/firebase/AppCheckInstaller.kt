package com.example.cybershield.core.firebase

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release build type: installs the Play Integrity App Check provider, which
 * performs real device/app attestation. This type only exists in the
 * `release` source set, mirroring [com.example.cybershield.core.firebase.AppCheckInstaller]
 * in `debug`.
 */
object AppCheckInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
