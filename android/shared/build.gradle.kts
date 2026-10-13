plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.loperstack.mochi.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // Mochi's sounds are the PC's own WAVs (NotchBuddy/Resources/sounds), copied in
    // at build time rather than kept twice in the repo. Personal use only: see
    // LICENSE-ASSETS.md at the root.
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/sounds"))
}

val copySounds = tasks.register<Copy>("copySounds") {
    from(rootDir.resolve("../NotchBuddy/Resources/sounds"))
    include("*.wav")
    exclude("greeting.wav")
    into(layout.buildDirectory.dir("generated/sounds/raw"))
}
tasks.named("preBuild") { dependsOn(copySounds) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.animation:animation")
    api("androidx.core:core-ktx:1.15.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    api("com.google.android.gms:play-services-wearable:18.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
