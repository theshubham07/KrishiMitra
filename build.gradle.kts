// build.gradle.kts (project-level)

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)    apply false
    id("com.google.gms.google-services")  version "4.4.2" apply false
}

// Note: **no** repositories { } or allprojects { } blocks here
