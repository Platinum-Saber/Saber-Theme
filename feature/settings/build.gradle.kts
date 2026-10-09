plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.compose)
    alias(libs.plugins.saber.hilt)
}

android {
    namespace = "com.sabertheme.feature.settings"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.icons)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
}
