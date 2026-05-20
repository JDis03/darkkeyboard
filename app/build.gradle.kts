plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "org.dark.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.dark.keyboard"
        minSdk = 21
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"

        // Solo incluir librerías nativas para arm64 (la mayoría de dispositivos modernos)
        // Esto reduce el APK de ~126MB a ~30MB
        ndk {
            abiFilters += "arm64-v8a"
            // Para debug en emulador x86, descomentar:
            // abiFilters += "x86_64"
        }
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

    buildFeatures {
        compose = true
    }

    // No compress tflite models
    androidResources {
        noCompress += "tflite"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true  // Robolectric accede a assets/
        }
    }
}

dependencies {
    // TFLite — Fase 3: re-ranker contextual
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    // GPU delegate eliminado — causa crashes en algunos dispositivos con tflite 2.16
    // NNAPI (disponible en Android 8.1+) se usa como acelerador si está disponible
    // select-tf-ops para T5 (operaciones adicionales)
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
    // SentencePiece: se carga via reflexión desde TFLite support si está disponible

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}