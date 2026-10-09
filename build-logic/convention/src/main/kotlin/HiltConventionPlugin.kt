import org.gradle.api.Plugin
import org.gradle.api.Project

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) { configure() }
    }

    private fun Project.configure() {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies.add("implementation", libs.lib("hilt-android"))
        dependencies.add("ksp", libs.lib("hilt-compiler"))
    }
}
