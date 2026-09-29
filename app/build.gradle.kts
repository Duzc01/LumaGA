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
        versionCode = 10116
        versionName = "1.1.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 腾讯 Bugly 崩溃监控。AppID 通过 gradle 属性注入（本地
        // gradle.properties 或 CI secrets），未配置时监控不启用。
        val buglyAppId = (project.findProperty("buglyAppId") as? String).orEmpty()
        buildConfigField("String", "BUGLY_APP_ID", "\"$buglyAppId\"")
        buildConfigField("boolean", "BUGLY_ENABLED", "${buglyAppId.isNotEmpty()}")
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

// Name the release APK after the app version, e.g. LumaGA_1.1.16.apk,
// so CI artifacts and manual Releases uploads carry the version.
androidComponents {
    val releaseVersionName = android.defaultConfig.versionName
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("LumaGA_${releaseVersionName}.apk")
        }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.bugly.crashreport)
    implementation(libs.bugly.nativecrashreport)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Instrumented tests (currently not run in CI; the emulator workflow was
    // removed because software-rendered emulators couldn't finish in time).
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // Local JVM unit tests (no Android framework needed).
    testImplementation("junit:junit:4.13.2")
}
