plugins {
    alias(libs.plugins.saber.android.application)
    alias(libs.plugins.saber.compose)
    alias(libs.plugins.saber.hilt)
}

android {
    namespace = "com.sabertheme.launcher"
    defaultConfig {
        applicationId = "com.sabertheme.launcher"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release code (R8, non-debuggable) signed with the debug key, for on-device perf checks.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            proguardFile("benchmark-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.icons)
    implementation(projects.core.ui)
    implementation(projects.feature.drawer)
    implementation(projects.feature.home)
    implementation(projects.feature.widgets)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.metrics.performance)
}
