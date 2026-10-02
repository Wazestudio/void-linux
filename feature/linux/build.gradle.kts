plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val targetAbi = providers.gradleProperty("targetAbi").orElse("arm64-v8a").get()
require(targetAbi in setOf("arm64-v8a", "armeabi-v7a")) {
    "Unsupported Android ABI: $targetAbi"
}

android {
    namespace = "com.voidlinux.feature.linux"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        buildConfigField("String", "TARGET_ABI", "\"$targetAbi\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/NOTICE*", "META-INF/LICENSE*", "META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:native"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.xz)
}
