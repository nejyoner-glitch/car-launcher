plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.carlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.carlauncher"
        minSdk = 21
        targetSdk = 33
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 30
        versionName = "2.1"
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

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
}
