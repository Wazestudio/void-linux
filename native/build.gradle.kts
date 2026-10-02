plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val targetAbi = providers.gradleProperty("targetAbi").orElse("arm64-v8a").get()
require(targetAbi in setOf("arm64-v8a", "armeabi-v7a")) {
    "Unsupported Android ABI: $targetAbi"
}

android {
    // CORRECTION : "native" est un mot-clé réservé en Java. Remplacé par "core_native"
    namespace = "com.voidlinux.core_native"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += targetAbi
        }
        externalNativeBuild {
            cmake {
                cFlags += listOf("-Wall", "-Wextra", "-O2", "-fPIC")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

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
