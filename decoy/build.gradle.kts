plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.pranav.decoy"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.pranav.decoy"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isCrunchPngs = false
            //noinspection NotShrinkingResources
            isShrinkResources = false
            isMinifyEnabled = true

            optimization.enable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources.additionalParameters.add("--no-compress")
    androidResources.additionalParameters.add("--no-version-vectors")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

