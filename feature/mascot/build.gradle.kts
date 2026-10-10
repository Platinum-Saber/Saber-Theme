plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.compose)
}

android {
    namespace = "com.sabertheme.feature.mascot"
}

dependencies {
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
}
