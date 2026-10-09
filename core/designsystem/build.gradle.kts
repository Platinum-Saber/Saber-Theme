plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.compose)
}

android {
    namespace = "com.sabertheme.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
}
