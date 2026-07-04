import java.util.Properties

plugins {
    alias(libs.plugins.android.application) // this is an app, not a library
    alias(libs.plugins.kotlin.serialization) // for @Serializable nav routes
    alias(libs.plugins.hilt) // Hilt DI
    alias(libs.plugins.ksp) // code generation (Hilt, Room)
    alias(libs.plugins.google.services) // Firebase — reads google-services.json
    alias(libs.plugins.firebase.crashlytics) // crash reporting
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.cybershield"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.cybershield"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner =
            "com.example.cybershield.HiltTestRunner"
    }
    testOptions {
        unitTests.all {
            it.failOnNoDiscoveredTests.set(false)
        }
    }

    signingConfigs {
        create("release") {
            val localPropsFile = rootProject.file("local.properties")
            val props =
                Properties().apply {
                    if (localPropsFile.exists()) {
                        load(localPropsFile.inputStream())
                    }
                }
            val keystorePath = props["KEYSTORE_PATH"] as String?
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = props["KEYSTORE_PASSWORD"] as String
                keyAlias = props["KEY_ALIAS"] as String
                keyPassword = props["KEY_PASSWORD"] as String
            }
        }
    }

    buildTypes {
        debug {
            // applicationIdSuffix = ".debug"      // installs alongside release
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true // R8 shrink + obfuscate
            isShrinkResources = true // remove unused resources
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // ── Compile options ───────────────────────────────────────────────
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ── Features ──────────────────────────────────────────────────────
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
tasks.configureEach {
    if (name.contains("uploadCrashlyticsMappingFile")) {
        enabled = true
    }
}


tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generates JaCoCo coverage report from debug unit tests."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*_Factory.*", "**/*_MembersInjector.*", "**/Hilt_*.*", "**/*_HiltModules*.*",
        "**/di/**", "**/*Module.*"
    )

    val mainSrcDir = layout.projectDirectory.dir("src/main/java")

    sourceDirectories.setFrom(files(mainSrcDir))
    classDirectories.setFrom(
        layout.buildDirectory.asFileTree.matching {
            include("tmp/kotlin-classes/debug/**")
            exclude(fileFilter)
        }
    )
    executionData.setFrom(
        layout.buildDirectory.asFileTree.matching {
            include("**/*.exec", "**/*.ec")
        }
    )
}

dependencies {
    // ── Compose ───────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    implementation(libs.foundation)
    implementation(libs.room.ktx)
    implementation(libs.ui.text)
    implementation(libs.androidx.splashscreen)
    implementation(libs.material)
    debugImplementation(libs.compose.ui.tooling)
    // ── Lifecycle ─────────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    // ── Navigation ────────────────────────────────────────────────────
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // ── Hilt ──────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // -- Room ----------------
    ksp(libs.room.compiler)

    // ── Firebase ──────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.coroutines)

    // ── Google Sign-In (Credential Manager) ───────────────────────────
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.google.id)

    // ── Media3 / ExoPlayer ────────────────────────────────────────────
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // ── Image loading ─────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Coroutines ────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── WorkManager ───────────────────────────────────────────────────
    implementation(libs.work.runtime)

    // ── Testing ───────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.hilt.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity)
}
