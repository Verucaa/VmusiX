plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

/**
 * Kredensial rilis TIDAK pernah ada di file ini.
 *
 * Cara isi (lokal):
 *   export VMUSIX_KEYSTORE=/path/ke/vmusix-release.jks
 *   export VMUSIX_KEYSTORE_PASSWORD=...
 *   export VMUSIX_KEY_ALIAS=vmusix
 *   export VMUSIX_KEY_PASSWORD=...
 *   ./gradlew assembleRelease
 *
 * Kalau keempatnya kosong, `signingConfig` = null -> APK release tetap
 * dibangun tapi UNSIGNED (Android menolak memasangnya). Itu Fail-Loud yang
 * sengaja: lebih baik error jelas daripada "install ditolak" tanpa sebab.
 *
 * Di CI, pakai GitHub Secrets (lihat .github/workflows/build.yml).
 */
val ksPath: String? = System.getenv("VMUSIX_KEYSTORE")
val ksPass: String? = System.getenv("VMUSIX_KEYSTORE_PASSWORD")
val keyAlias: String? = System.getenv("VMUSIX_KEY_ALIAS")
val keyPass: String? = System.getenv("VMUSIX_KEY_PASSWORD")
val hasSigning = listOf(ksPath, ksPass, keyAlias, keyPass).all { !it.isNullOrBlank() }

android {
    namespace = "com.zaaam.vmusix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zaaam.vmusix"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("id", "en")
    }

    if (hasSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = keyAlias
                keyPassword = keyPass
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // Output APK dinamai VmusiX-<variant>-<version>.apk supaya file di Releases
    // jelas terbaca tanpa harus menebak nama bawaan AGP ("app-release.apk").
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "VmusiX-$name-$versionName.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3 — pin eksplisit per modul, semua harus versi sama. Jangan pakai
    // BOM: NewPipeExtractor menarik Guava sendiri dan konkretnya bisa bentrok.
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    implementation("androidx.media3:media3-ui:1.9.0")
    implementation("androidx.media3:media3-common:1.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // NewPipeExtractor — pin eksak, JANGAN `+` atau `latest.release`: API-nya
    // sering berubah dan build bisa rusak sendiri tanpa ada yang mengubah kode.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // Coil (artwork lokal via content URI, thumbnail YT via URL)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Palette (ekstraksi warna dominan artwork -> gradient Now Playing)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Haze (blur konten di belakang untuk tab bar & mini-player)
    implementation("dev.chrisbanes.haze:haze:1.0.0")

    // OkHttp (downloader NewPipeExtractor)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.guava:guava:32.1.2-android")
}
