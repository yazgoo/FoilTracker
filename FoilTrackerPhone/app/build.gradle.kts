plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.foiltracker"

    compileSdk = 36
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    defaultConfig {
        applicationId = "com.example.foiltracker"

        minSdk = 26

        targetSdk = 36

        versionCode = 1

        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.17.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.11.0"
    )

    implementation(
        platform(
            "androidx.compose:compose-bom:2025.08.00"
        )
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.9.3"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.9.3"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3"
    )

    implementation(
        "androidx.room:room-runtime:2.7.2"
    )

    implementation(
        "androidx.room:room-ktx:2.7.2"
    )

    ksp(
        "androidx.room:room-compiler:2.7.2"
    )

    implementation(
        "com.google.android.gms:play-services-wearable:20.0.1"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
