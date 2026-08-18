plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bugenzhao.mnga.logic"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Generated protobuf code is checked in under src/main/java; regenerate it
    // from rust/protos with rust/gen-kotlin-protos.sh.
    // Exclude the transitive protobuf-java brought by protobuf-kotlin to keep a
    // single copy on the classpath.
    implementation(libs.protobuf.java)
    api(libs.protobuf.kotlin) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    api(libs.protobuf.util)
    api(libs.kotlinx.coroutines.android)
}
