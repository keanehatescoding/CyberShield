# ── Hilt ──────────────────────────────────────────────────────────────────────
# Hilt ships its own consumer R8 rules via the Gradle plugin. These rules handle
# the edge cases that the plugin's auto-rules don't cover (e.g. HiltViewModel
# when accessed reflectively through SavedStateHandle or navigation-compose).
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ── Firebase App Check ────────────────────────────────────────────────────────
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.appcheck.playintegrity.** { *; }

# ── Firebase — keep classes accessed reflectively by the SDK ──────────────────
# NOTE: -keep (not -keepnames) — R8 full mode can remove classes that are only
# referenced by string/reflection, and -keepnames alone won't prevent that.
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.auth.FirebaseAuth { *; }
-keep class com.google.firebase.auth.FirebaseUser { *; }
-keep class com.google.firebase.firestore.FirebaseFirestore { *; }
-keep class com.google.firebase.firestore.DocumentReference { *; }
-keep class com.google.firebase.firestore.CollectionReference { *; }
-keep class com.google.firebase.messaging.FirebaseMessagingService { *; }
-keep class com.google.firebase.storage.FirebaseStorage { *; }

# ── Custom @Keep annotated classes (Firestore DTOs, etc.) ────────────────────
# androidx.annotation.Keep on the class itself already prevents shrinking;
# this ensures R8 full mode doesn't strip members of those classes.
-keep @androidx.annotation.Keep class * {
    public *;
    <init>(...);
}

# ── Room ──────────────────────────────────────────────────────────────────────
# Room ships its own consumer rules, but R8 full mode needs explicit attribute
# preservation for the annotation-based query verification to work.
-keepattributes *Annotation*, Signature, Exception
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin serialization (Compose Navigation routes, data classes) ────────────
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep the serializer companion object for every @Serializable class so that
# R8 doesn't strip it even when no direct Kotlin reference to .serializer() exists.
-keep @kotlinx.serialization.Serializable class * {
    public static *** Companion;
}
# Discover kotlinx.serialization modules at compile time (avoids
# ServicesLoader fallback which can fail under R8 full mode).
-keepclassmembers class kotlinx.serialization.json.Json {
    *** Companion;
}
-keepclassmembers class kotlinx.serialization.json.JsonArray { *; }
-keepclassmembers class kotlinx.serialization.json.JsonObject { *; }
-keepclassmembers class kotlinx.serialization.json.JsonPrimitive { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
# -keepnames is sufficient here — these are referenced by META-INF/services
# and only need their names preserved, not their bodies.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    native <methods>;
}
# Prevent R8 from stripping Continuation/worker classes needed for
# coroutine suspension resumption across process boundaries.
-keepclassmembers class kotlin.coroutines.Continuation { *; }

# ── OkHttp (used by Coil internally) ─────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ── Google Sign-In / Credential Manager ───────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ── JetBrains annotations (used by several libraries via reflection) ─────────
-dontwarn org.jetbrains.annotations.**