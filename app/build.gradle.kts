plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.yeex.dlof"
    // Bumped from 34 -> 35: several deps (androidx.compose.* 1.8.1 pulled in
    // transitively by telephoto/richtext/lottie, etc.) declare an AAR
    // metadata requirement of compileSdk >= 35. Building against 34 failed
    // ':app:checkDebugAarMetadata' with 14 "requires ... version 35" errors.
    // AGP was bumped to 8.9.1 alongside this since AGP 8.5.0's max supported
    // compileSdk was 34.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yeex.dlof"
        // 23 (Android 6.0 Marshmallow) is the real floor for this project,
        // not an arbitrary choice: every other dependency here (Compose,
        // Media3, MLKit, Navigation) supports back to API 21, but
        // androidx.security:security-crypto — used by the local
        // multi-account store to encrypt saved credentials at rest — hard
        // -requires minSdk 23 in its own manifest. Going lower would either
        // fail the manifest merge or force dropping encrypted local storage.
        minSdk = 23
        targetSdk = 35
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
        // Needed for BuildConfig.VERSION_NAME/VERSION_CODE, used by
        // DataExportUtil's "الإبلاغ عن مشكلة" diagnostics block in Settings
        // & Privacy — AGP 8+ no longer generates BuildConfig by default.
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        disable += "MissingTranslation"
        abortOnError = false
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
    // GIF frame decoding for Coil — without this, a pasted GIF link preview
    // (LinkPreviewCard) or a GIF cover image just shows a frozen first
    // frame. Registered app-wide via YeexApp's ImageLoaderFactory.
    implementation("io.coil-kt:coil-gif:2.6.0")

    // ---- YEEX TOPICS | المواضيع — style/visual upgrade packages ----
    // Proper CommonMark rendering (headings, nested lists, tables, code
    // spans) as a polished alternative/upgrade path to MarkdownText.kt's
    // hand-rolled line parser for a topic's long-form body.
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.16.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:0.16.0")
    // Shimmer skeleton placeholders for the Topics list while it loads,
    // replacing the bare spinner with a shape-matched loading state.
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.3")
    // Lottie animations — used for the Topics empty state (see
    // res/raw/topics_empty.json) instead of plain text.
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    // Pinch-to-zoom / pan for a Topic's cover image and Link Card image —
    // a drop-in ZoomableAsyncImage replacement for Coil's AsyncImage.
    implementation("me.saket.telephoto:zoomable-image-coil:0.19.0")

    // Video playback for VIDEO-type paragraphs (feed previously tried to
    // decode raw MP4 bytes as a Bitmap, which returns null and crashes —
    // see MediaBase64.decodeToBitmap / VideoPlayer.kt).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Burns the yeex watermark + moving author bubble into every frame of a
    // downloaded video (see VideoWatermarkUtil / README "علامة مائية على كل
    // فريمات الفيديو"). Uses Media3's own decode-effect-encode pipeline
    // instead of pulling in ffmpeg-kit (~30-50MB extra) since media3-exoplayer
    // is already a dependency above — transformer/effect are the matching
    // same-version modules from the same library family, not a new stack.
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")

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
