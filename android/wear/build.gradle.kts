plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.loperstack.mochi.wear"
    compileSdk = 35

    defaultConfig {
        // The same id and key as the phone app: the Data Layer only links apps that match.
        applicationId = "mx.loperstack.mochi"
        minSdk = 33
        targetSdk = 35
        versionCode = 18
        versionName = "1.3.0"
    }

    signingConfigs {
        create("stable") {
            val path = System.getenv("MOCHI_KEYSTORE")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("MOCHI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MOCHI_KEY_ALIAS") ?: "mochi"
                keyPassword = System.getenv("MOCHI_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            if (System.getenv("MOCHI_KEYSTORE") != null) signingConfig = signingConfigs.getByName("stable")
        }
        // The build you install: R8-optimised, so Compose runs smooth on the watch.
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("MOCHI_KEYSTORE") != null) signingConfigs.getByName("stable")
            else signingConfigs.getByName("debug")
        }
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
    // Personal builds: lint runs on its own, it does not stop a release.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
