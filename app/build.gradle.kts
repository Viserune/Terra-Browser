plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.terra.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.terra.browser"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = "1.0.6"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

val useLocalLiquidKit = providers.gradleProperty("localLiquidKitDir")
    .orElse(providers.environmentVariable("LIQUIDKIT_KMP_DIR"))
    .orNull
    ?.let(::file)
    ?.isDirectory == true

dependencies {
    if (useLocalLiquidKit) {
        implementation(libs.liquidkit.android)
        implementation(libs.backdrop.android)
    } else {
        implementation(fileTree("libs") { include("*.aar") })
    }
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.kyant.shapes)
    testImplementation(libs.junit)
}
