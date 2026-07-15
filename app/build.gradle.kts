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
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(libs.mediapipe.llm.inference)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
