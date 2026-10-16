plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

android {
    namespace = "com.ammiratv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ammiratv"
        minSdk = 21
        targetSdk = 35
        versionCode = 10104
        versionName = "1.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Local test signing so a release build can be installed and tried on a device. A real
            // store upload needs its own key: replace this before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Same code base, three distribution channels. Nothing store-specific lives in the main flow:
    // no Google Play Services, Firebase, Cast or billing is used, so all three run on Fire OS.
    //  - play:     Google Play (Android phones, tablets, Android TV / Google TV)
    //  - amazon:   Amazon Appstore (Fire TV, Fire tablets)
    //  - sideload: direct APK install (ADB, Downloader app)
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"play\"")
        }
        create("amazon") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"amazon\"")
        }
        create("sideload") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"sideload\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Project modules
    implementation(project(":core:ui"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:player"))
    implementation(project(":core:vpn"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:live"))
    implementation(project(":feature:epg"))
    implementation(project(":feature:vod"))
    implementation(project(":feature:search"))
    implementation(project(":feature:multiview"))
    implementation(project(":feature:settings"))

    // Applies the baseline profile at install time (without it the profile is ignored)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)
    // Memory leak detection while developing; not in release builds
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Compose for TV
    implementation(libs.bundles.tv)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.bundles.lifecycle)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.junit.ext)
}
