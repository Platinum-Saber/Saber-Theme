plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.hilt)
}

android {
    namespace = "com.sabertheme.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
