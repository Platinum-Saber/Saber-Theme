plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.compose)
    alias(libs.plugins.saber.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabertheme.feature.widgets"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.icons)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
