# ============================================================
# Void-Linux — Règles ProGuard pour le module Tor
# ============================================================

# --- Bibliothèque Tor native (tor-android) ---
-keep class org.torproject.jni.** { *; }
-keep class org.torproject.jni.TorService { *; }
-keepclassmembers class org.torproject.jni.** {
    native <methods>;
}
-keepclasseswithmembernames class org.torproject.jni.** {
    native <methods>;
}

# --- Contrôleur Tor (jtorctl) ---
-keep class net.freehaven.tor.control.** { *; }
-keepclassmembers class net.freehaven.tor.control.** { *; }

# --- Classes JNI générées ---
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Ne pas avertir sur les classes optionnelles de Tor ---
-dontwarn org.torproject.jni.**
-dontwarn net.freehaven.tor.control.**
-dontwarn org.slf4j.**
-dontwarn org.eclipse.jetty.**
-dontwarn com.sun.**
-dontwarn javax.annotation.**
-dontwarn javax.servlet.**
-dontwarn org.bouncycastle.**

# --- Garder les attributs nécessaires ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# --- Modèles de données (sérialisation) ---
-keep class com.voidlinux.feature.tor.** { *; }