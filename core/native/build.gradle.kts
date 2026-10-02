plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val targetAbi = providers.gradleProperty("targetAbi").orElse("arm64-v8a").get()
require(targetAbi in setOf("arm64-v8a", "armeabi-v7a")) {
    "Unsupported Android ABI: $targetAbi"
}

android {
    namespace = "com.voidlinux.core.natives"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += targetAbi
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
