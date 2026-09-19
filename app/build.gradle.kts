plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {

    namespace =
        "com.athena.j.athena"

    compileSdk =
        36

    // =============================================================
    // DEFAULT CONFIG
    // =============================================================

    defaultConfig {

        applicationId =
            "com.athena.j.athena"

        minSdk =
            24

        targetSdk =
            36

        versionCode =
            2

        versionName =
            "1.1"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // =============================================================
    // BUILD TYPES
    // =============================================================

    buildTypes {

        release {

            isMinifyEnabled =
                true

            isShrinkResources =
                true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }

        debug {

            isMinifyEnabled =
                false

            isShrinkResources =
                false
        }
    }

    // =============================================================
    // JAVA / KOTLIN
    // =============================================================

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    kotlinOptions {

        jvmTarget =
            "11"
    }
}

dependencies {

    // =============================================================
    // ANDROID
    // =============================================================

    implementation(
        libs.appcompat
    )

    implementation(
        libs.material
    )

    implementation(
        libs.core.ktx
    )

    implementation(
        libs.lifecycle.runtime.ktx
    )

    // =============================================================
    // SECURITY / CRYPTOGRAPHY
    // =============================================================

    implementation(
        "org.bouncycastle:bcprov-jdk18on:1.78.1"
    )

    implementation(
        "androidx.security:security-crypto:1.1.0-alpha06"
    )

    implementation(
        "androidx.biometric:biometric-ktx:1.4.0-alpha02"
    )

    // =============================================================
    // STORAGE
    // =============================================================

    implementation(
        "androidx.documentfile:documentfile:1.0.1"
    )

    // =============================================================
    // TESTING
    // =============================================================

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.ext.junit
    )

    androidTestImplementation(
        libs.espresso.core
    )
}