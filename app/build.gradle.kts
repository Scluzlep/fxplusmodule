plugins {
    id("com.android.application")
}

android {
    namespace = "com.scluzlep.fxplusmodule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scluzlep.fxplusmodule"
        minSdk = 28
        targetSdk = 36
        versionCode = System.getenv("FXPLUS_VERSION_CODE")?.toIntOrNull() ?: 1004001
        versionName = System.getenv("FXPLUS_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.4"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
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
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.luckypray:dexkit:2.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
