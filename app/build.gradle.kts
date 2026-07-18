import java.util.Properties

plugins {
    alias(libs.plugins.android.application) // this is an app, not a library
    alias(libs.plugins.kotlin.serialization) // for @Serializable nav routes
    alias(libs.plugins.hilt) // Hilt DI
    alias(libs.plugins.ksp) // code generation (Hilt, Room)
    alias(libs.plugins.google.services) // Firebase — reads google-services.json
    alias(libs.plugins.firebase.crashlytics) // crash reporting
    alias(libs.plugins.compose.compiler)
    jacoco
}

android {
    namespace = "com.example.cybershield"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.cybershield"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner =
            "com.example.cybershield.HiltTestRunner"
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
                val storePass = props["KEYSTORE_PASSWORD"] as String?
                val alias = props["KEY_ALIAS"] as String?
                val keyPass = props["KEY_PASSWORD"] as String?
                require(storePass != null && alias != null && keyPass != null) {
                    "local.properties has KEYSTORE_PATH set but is missing one of " +
                        "KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD"
                }
                storeFile = file(keystorePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
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

// Room schema history — required now that CyberShieldDatabase sets
// exportSchema = true. Commit the JSON files this writes to app/schemas/;
// they're what Migration objects and MigrationTestHelper get diffed against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
// Mapping files only carry value for minified (release) builds — debug is
// never obfuscated, so uploading its "mapping" is pure waste: an extra
// network call to Firebase on every debug build for a file with nothing to
// deobfuscate, which is also mildly annoying for offline/local development.
tasks.configureEach {
    if (name == "uploadCrashlyticsMappingFileRelease") {
        enabled = true
    }
}


jacoco {
    // Gradle's bundled default lags behind on Kotlin coroutine/inline-function
    // debug-info handling; pin to a recent release for more accurate counts
    // on suspend functions, Flow operators, and inline fun call sites.
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generates JaCoCo coverage report from debug unit tests."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // NOTE: "**/*Module.*" used to be listed here to skip Hilt DI modules,
    // but every Hilt module in this codebase already lives under a `di/`
    // package and is caught by "**/di/**" below. The pattern was too broad:
    // it also matched core/domain/model/Module.kt — the app's core domain
    // entity — and silently excluded it from coverage. Removed.
    val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*_Factory.*", "**/*_MembersInjector.*", "**/Hilt_*.*", "**/*_HiltModules*.*",
        "**/di/**"
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

// Coverage floor for CI. Starts conservative (raise as suites grow) —
// the point is to catch *regressions*, not to certify a target number.
// Override locally with -PminCoverage=0.55 if you want to check a higher bar.
val minCoverage: String by lazy {
    (project.findProperty("minCoverage") as String?) ?: "0.40"
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    description = "Fails the build if instruction coverage from unit tests drops below the floor."
    dependsOn("jacocoTestReport")

    val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*_Factory.*", "**/*_MembersInjector.*", "**/Hilt_*.*", "**/*_HiltModules*.*",
        "**/di/**"
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

    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = minCoverage.toBigDecimal()
            }
        }
    }
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
    implementation(libs.room.paging)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
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
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.coroutines)
    implementation(libs.firebase.crashlytics)

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
    testImplementation(libs.paging.testing)
    testImplementation(libs.androidx.test.core.ktx)
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
