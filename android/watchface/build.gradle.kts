// Mochi's watch face, in the Watch Face Format (resource-only: no code, so it
// draws with almost no battery). The XML and images come from
// tools/make_watchface.py.
plugins {
    id("com.android.application")
}

android {
    namespace = "mx.loperstack.mochi.watchface"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.loperstack.mochi.watchface"
        // Watch Face Format 2: Wear OS 5 and up (the Pixel Watch 4 runs Wear OS 6).
        minSdk = 34
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
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
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (System.getenv("MOCHI_KEYSTORE") != null) signingConfigs.getByName("stable")
            else signingConfigs.getByName("debug")
        }
    }
}
