plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.navilas.finder"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.navilas.finder"
        minSdk = 26
        targetSdk = 35
        versionCode = 48
        versionName = "0.5.42-nightly"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("Boolean", "APP_UPDATE_ENABLED", "true")
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://raw.githubusercontent.com/Woszik/NaviLas-releases/main/latest.json\"",
            )
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_NIGHTLY_URL",
                "\"https://raw.githubusercontent.com/Woszik/NaviLas-releases/main/nightly.json\"",
            )
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_FINAL_URL",
                "\"https://raw.githubusercontent.com/Woszik/NaviLas-releases/main/final.json\"",
            )
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("Boolean", "APP_UPDATE_ENABLED", "false")
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"\"")
            buildConfigField("String", "UPDATE_MANIFEST_NIGHTLY_URL", "\"\"")
            buildConfigField("String", "UPDATE_MANIFEST_FINAL_URL", "\"\"")
        }
    }

    signingConfigs {
        val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")

    implementation("org.maplibre.gl:android-sdk:11.8.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
