import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Apply together with `saber.android.application` or `saber.android.library`. */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) { configure() }
    }

    private fun Project.configure() {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> { buildFeatures.compose = true }
        }
        dependencies.add("implementation", dependencies.platform(libs.lib("compose-bom")))
        dependencies.add("implementation", libs.lib("compose-ui"))
        dependencies.add("implementation", libs.lib("compose-foundation"))
        dependencies.add("implementation", libs.lib("compose-ui-tooling-preview"))
        dependencies.add("debugImplementation", libs.lib("compose-ui-tooling"))
    }
}
