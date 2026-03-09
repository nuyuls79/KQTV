plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ibypass.tvku"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibypass.tvku"
        minSdk = 21
        targetSdk = 35
        versionCode = 8
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    // Android Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ExoPlayer (Media3) - versi 1.5.0
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.drm)      // ← pastikan alias ini mengarah ke media3-exoplayer-drm
    implementation(libs.media3.datasource)
    implementation(libs.androidx.media3.datasource.rtmp)

    // Icons tambahan
    implementation(libs.material.icons.extended)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // AppCompat & Material
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Coil
    implementation(libs.coil.compose)

    // WorkManager & Concurrent
    implementation(libs.work.runtime.ktx)
    implementation(libs.concurrent.futures.ktx)

    // Lifecycle tambahan
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Library lain dari catalog
    implementation(libs.transport.api)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.foundation.android)
    implementation(libs.volley)

    // Accompanist
    implementation(libs.accompanist.placeholder.material)

    // Firebase BOM
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // LeakCanary
    debugImplementation(libs.leakcanary.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}