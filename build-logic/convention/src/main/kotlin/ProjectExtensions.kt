import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal object Sdk {
    const val COMPILE = 37 // current AndroidX requires it; device runs 36
    const val TARGET = 36
    const val MIN = 33 // AGSL RuntimeShader
}

internal val JAVA_VERSION = JavaVersion.VERSION_17

/**
 * Lint checks that guard against leaks and outside access (docs/security.md):
 * these fail the build instead of warning. Intentional cases carry a
 * `tools:ignore` with the reason.
 */
internal val SECURITY_LINT_ERRORS = setOf(
    "ExportedReceiver", "ExportedService", "ExportedContentProvider", "ExportedPreferenceActivity",
    "UnsafeIntentLaunch", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent",
    "UnsafeProtectedBroadcastReceiver", "DataExtractionRules", "HardcodedDebugMode",
    "TrustAllX509TrustManager", "InsecureBaseConfiguration", "SetJavaScriptEnabled",
    "WorldReadableFiles", "WorldWriteableFiles", "SecureRandom", "PackageManagerGetSignatures",
)

/** AGP 9 compiles Kotlin itself (built-in Kotlin); this only sets options and test deps. */
internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    dependencies.add("testImplementation", libs.lib("junit"))
    dependencies.add("testImplementation", libs.lib("truth"))
    dependencies.add("testImplementation", libs.lib("kotlinx-coroutines-test"))
}
