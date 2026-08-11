buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "8.9.1" apply false
    // Bumped from 1.9.24 -> 2.1.21: compose-shimmer (and transitively okio)
    // pulled kotlin-stdlib 2.1.21 onto the classpath, but the 1.9.24 Kotlin
    // compiler can only read metadata up to version 2.0.0 -- this mismatch
    // is what caused ":app:compileDebugKotlin" to fail with "Incompatible
    // classes were found in dependencies" and the resulting cascade of
    // "Unresolved reference: error" (kotlin.error()) / enum-comparison
    // errors across the codebase. Matching the plugin version to the
    // resolved stdlib version (2.1.21) removes the mismatch.
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Required as of Kotlin 2.0+: the Compose compiler is no longer bundled
    // with the Kotlin Gradle plugin and configured via
    // composeOptions.kotlinCompilerExtensionVersion (see app/build.gradle.kts,
    // that block is removed) -- it's now this separate plugin, versioned to
    // match the Kotlin plugin above.
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
