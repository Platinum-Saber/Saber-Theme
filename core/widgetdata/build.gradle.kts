plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabertheme.core.widgetdata"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
