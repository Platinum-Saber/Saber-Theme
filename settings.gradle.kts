pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx (Saber's on-device voice) is only published here; nothing else may come from it.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.k2-fsa.sherpa-onnx") }
        }
    }
}

rootProject.name = "Saber-Theme"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:designsystem",
    ":core:icons",
    ":core:ui",
    ":core:widgetdata",
    ":core:briefing",
    ":feature:drawer",
    ":feature:home",
    ":feature:mascot",
    ":feature:settings",
    ":feature:widgets",
    ":widgets-glance",
    ":iconpack",
)
