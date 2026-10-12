import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gesturekey.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gesturekey.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // A fixed debug key (committed on purpose) so every CI build installs over the last one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
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

    // MediaPipe memory-maps the model, so keep it uncompressed in the APK.
    androidResources {
        noCompress.add("task")
    }
}

dependencies {
    // Settings screen.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // Camera preview + frames for analysis.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Google's on-device gesture recognizer (canned 8-class model).
    implementation("com.google.mediapipe:tasks-vision:0.10.21")

    testImplementation("junit:junit:4.13.2")
}

// Fetch Google's canned gesture model at build time (not committed).
val gestureModelUrl =
    "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/latest/gesture_recognizer.task"
val gestureModelFile = layout.projectDirectory.file("src/main/assets/gesture_recognizer.task")

val downloadGestureModel by tasks.registering {
    outputs.file(gestureModelFile)
    doLast {
        val target = gestureModelFile.asFile
        if (!target.exists()) {
            target.parentFile.mkdirs()
            URI(gestureModelUrl).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadGestureModel)
}
