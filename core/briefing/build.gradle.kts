plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabertheme.core.briefing"
}

dependencies {
    implementation(projects.core.widgetdata)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.litertlm.android)
    implementation(libs.sherpa.onnx.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.multiprocess)
}
