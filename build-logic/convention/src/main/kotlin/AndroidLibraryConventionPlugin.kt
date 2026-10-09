import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) { configure() }
    }

    private fun Project.configure() {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = Sdk.COMPILE
            defaultConfig { minSdk = Sdk.MIN }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
        }
        configureKotlinAndroid()
    }
}
