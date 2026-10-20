plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Firebase Google Services
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.blooddononerapp"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.blooddononerapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Existing dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // =========================
    // Firebase
    // =========================

    // Firebase Bill of Materials
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")

    // Cloud Firestore Database
    implementation("com.google.firebase:firebase-firestore")

    // =========================
    // Google Sign-In - Credential Manager
    // =========================
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // =========================
    // Testing
    // =========================

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}