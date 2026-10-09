plugins {
    `kotlin-dsl`
}

group = "com.sabertheme.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "saber.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "saber.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("compose") {
            id = "saber.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("hilt") {
            id = "saber.hilt"
            implementationClass = "HiltConventionPlugin"
        }
    }
}
