import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.glypdl.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glypdl.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "3.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }

        create("release") {
            val envPath = System.getenv("RELEASE_KEYSTORE_PATH")
            val keystoreFile = when {
                !envPath.isNullOrBlank() -> file(envPath)
                file("../glypdl-release.jks").exists() -> file("../glypdl-release.jks")
                file("glypdl-release.jks").exists() -> file("glypdl-release.jks")
                else -> null
            }

            val storePasswordEnv = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("RELEASE_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("RELEASE_KEY_PASSWORD")

            if (keystoreFile != null && keystoreFile.exists() && !storePasswordEnv.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv ?: "glypdl"
                keyPassword = keyPasswordEnv ?: storePasswordEnv
                enableV1Signing = true
                enableV2Signing = true
            } else {
                // Fallback to debug keystore for local development without release secrets
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.register("downloadLatestYtDlp") {
    description = "Downloads the latest official yt-dlp binary into res/raw/ytdlp"
    group = "build setup"
    val rawDir = file("src/main/res/raw")
    val targetFile = file("src/main/res/raw/ytdlp")
    outputs.file(targetFile)
    doLast {
        rawDir.mkdirs()
        println("Checking and bundling latest official yt-dlp release from GitHub...")
        try {
            val url = URI("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("Successfully downloaded latest yt-dlp (${targetFile.length()} bytes)")
        } catch (e: Exception) {
            if (targetFile.exists() && targetFile.length() > 0) {
                println("Using cached/existing yt-dlp binary (${targetFile.length()} bytes): ${e.message}")
            } else {
                throw GradleException("Failed to download latest yt-dlp and no cached binary exists: ${e.message}", e)
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("downloadLatestYtDlp")
}

