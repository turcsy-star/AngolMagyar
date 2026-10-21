plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "hu.angolmagyar.fordito"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.angolmagyar.fordito"
        minSdk = 29
        targetSdk = 35
        versionCode = 2101
        versionName = "2.10.1"

        // A telefon (Honor PGT-N19) arm64; így kisebb az APK.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Állandó debug kulcs – ugyanaz a signature minden buildnél (app/debug.keystore)
    signingConfigs {
        create("debugPermanent") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugPermanent")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugPermanent")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // A Vosk a JNA-n keresztül tölti be a natív könyvtárat, ehhez kicsomagolt .so kell.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Offline beszédfelismerés (Vosk + JNA)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Offline fordítás
    implementation("com.google.mlkit:translate:17.0.3")

    // Valódi nyelvfelismerés (on-device Language ID)
    implementation("com.google.mlkit:language-id:17.0.6")

    // ONNX Runtime – speaker embedding diarization
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    // Wear OS Data Layer (óra companion)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    testImplementation("junit:junit:4.13.2")
}
