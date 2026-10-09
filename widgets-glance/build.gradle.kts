plugins {
    alias(libs.plugins.saber.android.library)
    alias(libs.plugins.saber.compose)
    alias(libs.plugins.saber.hilt)
}

android {
    namespace = "com.sabertheme.widgets.glance"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.icons)
    implementation(projects.core.widgetdata)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
