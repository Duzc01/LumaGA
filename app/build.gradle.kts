import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bugenzhao.mnga"
    compileSdk = 36

    // Release signing credentials. The keystore and passwords live in
    // app/release-keystore.properties, which is git-ignored; a backup copy is
    // kept under ~/Documents/LumaGA-release-signing/. Without the properties
    // file (e.g. fresh clones) the release build stays unsigned.
    val releaseKeystorePropertiesFile = rootProject.file("app/release-keystore.properties")
    val releaseKeystoreProperties = Properties().apply {
        if (releaseKeystorePropertiesFile.exists()) {
            releaseKeystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    defaultConfig {
        applicationId = "com.bugenzhao.mnga"
        minSdk = 26
        targetSdk = 36
        versionCode = 113
        versionName = "1.1.3"
    }

    signingConfigs {
        // Checked-in debug keystore (standard debug credentials) so CI builds
        // are signature-compatible with local builds and can upgrade-install
        // over them.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing (credentials from app/release-keystore.properties,
        // see the note at the top of the android block).
        create("release") {
            if (releaseKeystorePropertiesFile.exists()) {
                storeFile = file(releaseKeystoreProperties["storeFile"] as String)
                storePassword = releaseKeystoreProperties["storePassword"] as String
                keyAlias = releaseKeystoreProperties["keyAlias"] as String
                keyPassword = releaseKeystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":logic"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
