import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("dev.rikka.tools.refine") version "4.4.0"
}

android {
    namespace = "dev.pranav.applock"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.pranav.applock"
        minSdk = 26
        targetSdk = 37
        versionCode = 243
        versionName = "2.4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            vcsInfo.include = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    buildFeatures {
        compose = true
        aidl = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(project(":appintro"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.refine.runtime)
    compileOnly(project(":hidden-api"))
    implementation(libs.hiddenapibypass)
    implementation(project(":patternlock"))

    implementation("com.github.apk-editor:aXML:dc872da9af")
    implementation(files("libs/apktool-lib.jar"))
    implementation(files("libs/brut.j.util.jar"))
    implementation("org.apktool:brut.j.common:3.0.2")
    implementation("org.apktool:brut.j.dir:3.0.2") {
        isTransitive = false
    }
    implementation("org.apktool:brut.j.xml:3.0.2")
    implementation("org.apktool:brut.j.yaml:3.0.2")
    implementation("com.github.iBotPeaches.smali:smali-baksmali:b6365a84f4")
    implementation("com.github.iBotPeaches.smali:smali:b6365a84f4")
    implementation("com.google.guava:guava:33.6.0-android")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("commons-io:commons-io:2.22.0")
    implementation("org.apache.commons:commons-text:1.15.0")
    implementation("ar.com.hjg:pngj:2.1.0")


    implementation(files("libs/apktool-android-1.0.0.aar"))

    implementation("com.android.tools.build:apksig:9.2.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
