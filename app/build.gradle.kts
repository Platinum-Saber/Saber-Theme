import java.util.Properties

plugins {
    alias(libs.plugins.saber.android.application)
    alias(libs.plugins.saber.compose)
    alias(libs.plugins.saber.hilt)
}

// Release signing from the untracked keystore.properties at the repo root
// (storeFile, storePassword, keyAlias, keyPassword). Without it, release
// builds come out unsigned.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "com.sabertheme.launcher"
    defaultConfig {
        applicationId = "com.sabertheme.launcher"
        // major * 10000 + minor * 100 + patch
        versionCode = 10000
        versionName = "1.0.0"
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
    implementation(projects.core.widgetdata)
    implementation(projects.feature.drawer)
    implementation(projects.feature.home)
    implementation(projects.feature.mascot)
    implementation(projects.feature.settings)
    implementation(projects.feature.widgets)
    implementation(projects.widgetsGlance)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.metrics.performance)
}
