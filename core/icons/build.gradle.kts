plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.compose)
}

android {
    namespace = "com.sabertheme.core.icons"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
