import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

android {
    namespace = "hu.angolmagyar.fordito"
    compileSdk = 35
    // Rögzített NDK: a GitHub runner előtelepített 27.0.12077973 mappája hiányos (nincs source.properties)
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "hu.angolmagyar.fordito"
        minSdk = 29
        targetSdk = 35
        versionCode = 4500
        versionName = "2.45.0"

        // A telefon (Honor PGT-N19) arm64; így kisebb az APK.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release")
                cppFlags += listOf("-O3")
            }
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

    // Saját release kulcs: a projekt gyökerében lévő keystore.properties (storeFile, storePassword, keyAlias, keyPassword)
    // vagy KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD környezeti változók. Ha egyik sincs,
    // a release a debug kulccsal íródik alá (csak saját tesztelésre).
    val ksProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun ksValue(prop: String, env: String): String? = ksProps.getProperty(prop) ?: System.getenv(env)
    val relStore = ksValue("storeFile", "KEYSTORE_FILE")
    if (relStore != null && file(relStore).exists()) {
        signingConfigs.create("release") {
            storeFile = file(relStore)
            storePassword = ksValue("storePassword", "KEYSTORE_PASSWORD")
            keyAlias = ksValue("keyAlias", "KEY_ALIAS")
            keyPassword = ksValue("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugPermanent")
        }
        release {
            // R8 (kisebb APK): ./gradlew :app:assembleRelease -Pminify  – előbb telefonon próbáld ki a Vosk/JNA/ONNX/Whisper útvonalakat!
            isMinifyEnabled = project.hasProperty("minify")
            isShrinkResources = project.hasProperty("minify")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debugPermanent")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            // A Vosk a JNA-n keresztül tölti be a natív könyvtárat, ehhez kicsomagolt .so kell.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose + Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

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

    // Room (TranslationMemory jövőbeli Room-alapú változathoz)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")

    // Compose screenshot smoke test (Robolectric + Roborazzi)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.44.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.44.0")
}
