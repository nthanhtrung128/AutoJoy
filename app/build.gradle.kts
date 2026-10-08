plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vietha.autojoy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vietha.autojoy"
        minSdk = 26          // cần Android 8.0+ để giữ joystick (continueStroke)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-P0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Không dùng thư viện ngoài: chỉ API Android gốc, để APK nhỏ và dễ build.
