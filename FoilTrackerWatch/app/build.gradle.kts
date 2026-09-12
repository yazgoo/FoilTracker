import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val buildDate = SimpleDateFormat(
    "yyyy-MM-dd HH:mm",
    Locale.US
).format(Date())

android {
    namespace = "org.piouz.pumpfoil"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.piouz.pumpfoil"

        minSdk = 30
        targetSdk = 37

        versionCode = providers.gradleProperty("versionCode")
            .orElse("2")
            .get()
            .toInt()

        versionName = providers.gradleProperty("versionName")
            .orElse("0.0.0")
            .get()

        buildConfigField(
            "String",
            "BUILD_DATE",
            "\"$buildDate\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    useLibrary("wear-sdk")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("upload-keystore.jks")

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")

            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)

    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)

    implementation("androidx.health:health-services-client:1.0.0-rc02")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.wear.compose:compose-material3")
    implementation("androidx.wear:wear:1.3.0")
}
