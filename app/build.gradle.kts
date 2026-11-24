plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.scluzlep.fxplusmodule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scluzlep.fxplusmodule"
        minSdk = 28
        targetSdk = 36
        versionCode = System.getenv("FXPLUS_VERSION_CODE")?.toIntOrNull() ?: 1002001
        versionName = System.getenv("FXPLUS_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("top.yukonga.miuix.kmp:miuix-android:0.7.2")
    implementation("org.jetbrains.compose.runtime:runtime:1.9.3")
    implementation("org.jetbrains.compose.foundation:foundation:1.9.3")
    implementation("org.jetbrains.compose.ui:ui:1.9.3")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
