plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {

    namespace =
        "com.athena.j.athena"

    compileSdk =
        36

    defaultConfig {

        applicationId =
            "com.athena.j.athena"

        minSdk =
            24

        targetSdk =
            36

        versionCode =
            3

        versionName =
            "2.1"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

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

    implementation(
        "org.bouncycastle:bcprov-jdk18on:1.78.1"
    )

    implementation(
        "androidx.security:security-crypto:1.1.0-alpha06"
    )

    implementation(
        "androidx.biometric:biometric-ktx:1.4.0-alpha02"
    )

    implementation(
        "androidx.documentfile:documentfile:1.0.1"
    )

    implementation(
        "com.nulab-inc:zxcvbn:1.9.0")


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