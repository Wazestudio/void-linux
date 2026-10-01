# Void-Linux ProGuard rules

# Garder les classes JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Garder NativeBridge
-keep class com.voidlinux.core.native.NativeBridge { *; }

# Garder ProotEngine
-keep class io.oonid.proot.engine.** { *; }

# Garder les modèles sérialisés
-keep class com.voidlinux.core.common.** { *; }
-keep class com.voidlinux.feature.**.model.** { *; }

# Garder Orbot
-keep class org.torproject.android.** { *; }
-dontwarn org.torproject.android.**

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# AndroidX
-dontwarn androidx.**

# Attributs inutiles
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions