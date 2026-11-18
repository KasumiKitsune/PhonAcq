# PhonAcq Desktop Pure Dead-Code Elimination Configuration
# Strictly disables symbol obfuscation and bytecode instruction rewriting so zero reflection,
# JNI, SQLite, Swing, or Compose runtime regressions can occur.

-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable,Exceptions

# Application entry points & build-time icon / typography verifiers
-keep class com.example.DesktopMainKt { *; }
-keep class com.example.DesktopIconRenderer { *; }
-keep class com.example.ui.theme.DesktopTypographyVerificationRunner { *; }
-keep class com.example.BuildConfig { *; }

# Keep PhonAcq data entities, DAOs, database layer, and wireless link protocol models
-keep class com.example.data.** { *; }
-keep class com.example.link.** { *; }

# Keep Android-to-Desktop compatibility bridge classes referenced across shared screens
-keep class android.** { *; }
-keep class androidx.room.** { *; }

# Keep org.json completely intact
-keep class org.json.** { *; }

# Keep SQLite JDBC driver & all JNI callback targets (NativeDB, DB, Function, BusyHandler, ProgressHandler)
-keep class org.sqlite.** { *; }
-dontwarn org.sqlite.**

# Keep Skiko / Skia native JNI bridges, AWT interop, and Compose Desktop font cache / fallback chain fields
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class androidx.compose.ui.text.platform.FontLoadResult { *; }
-keep class androidx.compose.ui.text.platform.FontCache { *; }
-keep class androidx.compose.ui.text.platform.PlatformFont_skikoKt { *; }
-keep class androidx.compose.ui.text.font.SkiaFontLoader { *; }
-keep class androidx.compose.ui.text.font.FontFamilyResolverImpl { *; }
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep Kotlin Coroutines ServiceLoader dispatchers (Swing MainDispatcherFactory) & volatile atomic fields
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class * implements kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class * implements kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.swing.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin enum values()/valueOf() and Serializable hooks
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Silence benign optional JDK / annotation / compileOnly dependency warnings
# - kotlinx.datetime (pulled transitively by Material3) references optional compileOnly kotlinx.serialization
# - DesktopLifecycleBridge intentionally shadows unused Jetpack lifecycle-viewmodel-desktop helpers
-dontwarn kotlinx.datetime.**
-dontwarn kotlinx.serialization.**
-dontwarn androidx.lifecycle.**
-dontwarn sun.misc.**
-dontwarn java.lang.instrument.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.intellij.lang.annotations.**
-dontwarn org.jetbrains.annotations.**
-dontnote kotlinx.coroutines.**
-dontnote com.example.**

