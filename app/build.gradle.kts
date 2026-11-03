import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
        versionCode = 40700
        versionName = "4.5.0"

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

    // A meglévő debug aláírás megmarad, így a v4.2.0-ról telepített tesztpéldány frissíthető.
    // Release buildnél kizárólag külön release-keystore használható; debug kulcsra soha nem esünk vissza.
    val ksProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun ksValue(prop: String, env: String): String? = ksProps.getProperty(prop) ?: System.getenv(env)
    val relStore = ksValue("storeFile", "KEYSTORE_FILE")
    val relStorePassword = ksValue("storePassword", "KEYSTORE_PASSWORD")
    val relKeyAlias = ksValue("keyAlias", "KEY_ALIAS")
    val relKeyPassword = ksValue("keyPassword", "KEY_PASSWORD")
    val releaseValues = listOf(relStore, relStorePassword, relKeyAlias, relKeyPassword)
    val releaseConfigRequested = releaseValues.any { !it.isNullOrBlank() }
    val releaseConfigComplete = releaseValues.all { !it.isNullOrBlank() }

    if (releaseConfigRequested && !releaseConfigComplete) {
        throw GradleException("A release aláírás hiányos. Add meg mind a négy keystore beállítást, vagy hagyd mindet üresen az unsigned release buildhez.")
    }
    if (releaseConfigComplete) {
        val releaseStoreFile = file(requireNotNull(relStore))
        if (!releaseStoreFile.isFile) {
            throw GradleException("A release keystore nem található: ${releaseStoreFile.absolutePath}")
        }
        signingConfigs.create("release") {
            storeFile = releaseStoreFile
            storePassword = requireNotNull(relStorePassword)
            keyAlias = requireNotNull(relKeyAlias)
            keyPassword = requireNotNull(relKeyPassword)
        }
    }

    signingConfigs {
        create("debugPermanent") {
            // Nyilvános, kizárólag debug célú Android tesztkulcs; release aláírásra soha nem használható.
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
            // R8 (kisebb APK): ./gradlew :app:assembleRelease -Pminify
            // Saját kulcs hiányában az APK szándékosan unsigned marad; debug kulccsal nem írjuk alá.
            isMinifyEnabled = project.hasProperty("minify")
            isShrinkResources = project.hasProperty("minify")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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


    packaging {
        jniLibs {
            // A Vosk a JNA-n keresztül tölti be a natív könyvtárat, ehhez kicsomagolt .so kell.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose + Material 3
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Egységtesztek (a core modul tiszta Kotlin logikája külön, a :core tesztjeiben van)
    testImplementation("junit:junit:4.13.2")

    // Offline beszédfelismerés (Vosk + JNA)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Offline fordítás
    implementation("com.google.mlkit:translate:17.0.3")

    // Valódi nyelvfelismerés (on-device Language ID)
    implementation("com.google.mlkit:language-id:17.0.6")
    // P8: eszközön futó szövegfelismerés (OCR), latin betűs szövegekhez
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ONNX Runtime – speaker embedding diarization
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    // Wear OS Data Layer (óra companion)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Room (TranslationMemory jövőbeli Room-alapú változathoz)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")


}
