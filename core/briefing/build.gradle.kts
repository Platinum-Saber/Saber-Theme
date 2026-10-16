plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabertheme.core.briefing"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
