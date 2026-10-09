// Standalone icon pack; every icon resource comes from `node tools/build-iconpack.mjs`.
plugins {
    alias(libs.plugins.saber.android.application)
}

android {
    namespace = "com.sabertheme.iconpack"
    defaultConfig {
        applicationId = "com.sabertheme.iconpack"
        versionCode = 1
        versionName = "0.1.0"
    }
}
