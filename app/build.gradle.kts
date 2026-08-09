plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.yeex.dlof"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yeex.dlof"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Compose ships several APIs (Material3, Pager, etc.) behind opt-in
        // annotations. Without this, using them fails the build with
        // "This API is experimental..." compile errors (not just warnings).
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.6.0")

    // Video playback for VIDEO-type paragraphs (feed previously tried to
    // decode raw MP4 bytes as a Bitmap, which returns null and crashes —
    // see MediaBase64.decodeToBitmap / VideoPlayer.kt).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Local multi-account store (encrypted at rest via Android Keystore) used
    // by AuthRepository/LocalAccountStore to remember every "معرف" that has
    // signed in on this device and let ProfileScreen's account switcher swap
    // between them instantly without retyping a password each time.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // On-device caption translation (ترجمة) for the paragraph rail's Translate
    // action — no API key; the first use of a given language pair downloads
    // a small model (a few MB) and every translation after that works fully
    // offline. Language ID auto-detects the caption's source language so the
    // person never has to pick one manually.
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
