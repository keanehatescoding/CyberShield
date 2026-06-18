# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ── Firebase Firestore ────────────────────────────────────────────────────────
# DTOs need all fields for reflection-based deserialization
-keep class com.example.cybershield.core.firebase.model.** { *; }
-keepclassmembers class com.example.cybershield.core.firebase.model.** {
    <init>();
    <fields>;
}

# ── Firebase — keep only what Firestore/Auth/FCM need at runtime ──────────────
-keepnames class com.google.firebase.FirebaseApp { *; }
-keepnames class com.google.firebase.auth.FirebaseAuth { *; }
-keepnames class com.google.firebase.firestore.FirebaseFirestore { *; }
-keepnames class com.google.firebase.messaging.FirebaseMessagingService { *; }

# ── Domain models ─────────────────────────────────────────────────────────────
-keep class com.example.cybershield.core.domain.model.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class com.example.cybershield.core.database.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class com.example.cybershield.core.sync.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin serialization (Compose Navigation routes) ──────────────────────────
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Suppress known safe warnings ──────────────────────────────────────────────
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler