plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.johnathaningle.excerpter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.johnathaningle.excerpter"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        val releaseStoreFile = project.findProperty("RELEASE_STORE_FILE") as? String
        if (!releaseStoreFile.isNullOrEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String ?: ""
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String ?: ""
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String ?: ""
            }
        }
    }

    buildTypes {
        release {
            val releaseStoreFile = project.findProperty("RELEASE_STORE_FILE") as? String
            if (!releaseStoreFile.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.pdfbox.android)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(libs.llmedge) {
        exclude(group = "io.gitlab.shubham0204", module = "sentence-embeddings")
    }
    // vision-internal-vkp 18.2.2 (pulled transitively by text-recognition) ships a 4KB-aligned
    // libmlkitcommonpipeline.so; 18.2.3 is 16KB-aligned. Pin it or 16KB-page devices crash.
    implementation("com.google.mlkit:vision-internal-vkp:18.2.3")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val llmModelPath = (project.findProperty("LLM_MODEL_PATH") as? String)
    ?: "/home/grey/.lmstudio/models/lmstudio-community/gemma-4-E2B-it-GGUF/gemma-4-E2B-it-Q4_K_M.gguf"
val llmModelName = "gemma-4-E2B-it-Q4_K_M.gguf"
val sdkDir = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.startsWith("sdk.dir=") }
    ?.substringAfter("=")
    ?.trim()
    ?: System.getenv("ANDROID_HOME")
val adb = "$sdkDir/platform-tools/adb"

// Push the local GGUF into the app's filesDir after install so the model is present
// without re-downloading. Override the path with -PLLM_MODEL_PATH=/path/to/model.gguf
val pushLlmModelTmp = tasks.register<Exec>("pushLlmModelTmp") {
    group = "install"
    commandLine(adb, "push", llmModelPath, "/data/local/tmp/$llmModelName")
}
val pushLlmModel = tasks.register<Exec>("pushLlmModel") {
    group = "install"
    description = "Push the local GGUF model into the app's files dir"
    dependsOn(pushLlmModelTmp)
    val script = "mkdir -p files && cp /data/local/tmp/$llmModelName files/$llmModelName"
    commandLine(
        adb, "shell", "run-as", "com.johnathaningle.excerpter", "sh", "-c",
        "\"$script\""
    )
}
tasks.matching { it.name.startsWith("install") }.configureEach { finalizedBy(pushLlmModel) }
