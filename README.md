# CyberShield

CyberShield is a gamified cybersecurity education app for Android, built as a third year academic project at Strathmore University under the supervision of Mr. Tiberius Tabulu. The app teaches cybersecurity concepts to university students through interactive learning modules, quizzes, XP and badges, and a leaderboard.

## Features

- **Authentication** — Email and password sign-up/sign-in and email verification, built on Firebase Auth
- **Learning modules** — Video-based lessons (ExoPlayer/Media3) with progress tracking
- **Quizzes** — Per-module quizzes with scoring, XP rewards, and offline-aware result syncing
- **Profile & certificates** — User XP/level tracking and generated completion certificates
- **Leaderboard** — Ranks users by XP earned
- **Offline support** — Room database caches modules and quiz results, with background sync via WorkManager when connectivity returns
- **Server-side quiz grading** — Quiz answers are validated and finalized in Cloud Functions rather than trusted from the client, with XP, badges, module completion, and certificates all written server-side

## Tech stack

- **UI**: Jetpack Compose, Material 3, Navigation Compose (type-safe routes)
- **Architecture**: MVVM with Clean Architecture layering (data / domain / feature)
- **DI**: Hilt
- **Backend**: Firebase (Auth, Firestore, Storage, Cloud Messaging, Crashlytics, App Check) + Cloud Functions (TypeScript) for server-side quiz grading and validation
- **Local storage**: Room
- **Background work**: WorkManager
- **Media**: Media3 / ExoPlayer
- **Async**: Kotlin Coroutines & Flow
- **Image loading**: Coil
- **Testing**: JUnit, MockK, Turbine, Compose UI testing, Hilt testing (app) · Vitest (Cloud Functions) · Firebase Rules Unit Testing (Firestore security rules)

See `gradle/libs.versions.toml` for exact dependency versions, and `functions/package.json` for Cloud Functions dependencies.

## Requirements

- Android Studio (latest stable)
- JDK 17+
- Android SDK with `compileSdk` / `targetSdk` 37 installed
- A Firebase project with `google-services.json` placed in `app/`

## Getting started

1. Clone the repo:
   ```bash
   git clone https://github.com/keanehatescoding/cybershield.git
   ```
2. Create a Firebase project and add an Android app with package name `com.example.cybershield`.
3. Download the generated `google-services.json` and place it in `app/`.
4. Open the project in Android Studio and let Gradle sync.
5. Run the `app` configuration on an emulator or device (minSdk 26+).

## Project structure

```
app/src/main/java/com/example/cybershield/
├── core/
│   ├── data/          # Repository implementations, DI bindings
│   ├── database/      # Room entities, DAOs
│   ├── domain/        # Models, repository interfaces, use cases
│   ├── firebase/      # Firestore/Auth/Storage data sources
│   ├── network/       # Network interceptors
│   ├── sync/          # WorkManager sync jobs
│   └── ui/            # Shared composables and theming
├── feature/
│   ├── auth/          # Sign-in, sign-up, email verification
│   ├── home/          # Home dashboard
│   ├── modules/       # Learning module list and detail/video screens
│   ├── quiz/          # Quiz flow and results
│   ├── profile/       # Profile, XP, certificates
│   └── leaderboard/   # XP leaderboard
└── ui/theme/          # App-wide Compose theme

functions/               # Cloud Functions: server-side quiz grading, validation, finalize/complete flows
firestore-tests/        # Firebase Rules Unit Testing suite for firestore.rules
firestore.rules         # Firestore security rules (client reads, server-only writes for XP/badges/certificates)
```

The project currently ships as a single `:app` module, with `core` and `feature` organized as packages rather than separate Gradle modules. The build is structured to make a future split into multi-module builds straightforward.

## Testing

Unit tests (ViewModels, use cases, sync worker) live in `app/src/test`, using MockK, Turbine, and fake repository implementations. Instrumented tests live in `app/src/androidTest`.

Run unit tests:

```bash
./gradlew test
```

Run instrumented tests (requires a connected device or emulator):

```bash
./gradlew connectedAndroidTest
```

Cloud Functions unit tests (in `functions/`):

```bash
npm ci
npm test
```

Firestore security rules tests (in `firestore-tests/`, requires the Firebase emulator):

```bash
npm ci
npm test
```

## Code style

The project uses [ktlint](https://github.com/jlleitschuh/ktlint-gradle), applied to all subprojects. Check formatting with:

```bash
./gradlew ktlintCheck
```

## Continuous integration

GitHub Actions CI is configured in `.github/workflows/ci.yml`. Dependabot watches Gradle dependencies and GitHub Actions versions weekly (`.github/dependabot.yml`).

## Academic context

This project is developed as part of an undergraduate Computer Science research project at Strathmore University, covering both a formal research proposal and a production-grade implementation of the CyberShield Android application.
