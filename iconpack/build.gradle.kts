import java.util.Properties

// Standalone icon pack; every icon resource comes from `node tools/build-iconpack.mjs`.
plugins {
    alias(libs.plugins.saber.android.application)
}

// Signed like the launcher: from the untracked keystore.properties at the repo
// root; without it, release builds come out unsigned.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "com.sabertheme.iconpack"
    defaultConfig {
        applicationId = "com.sabertheme.iconpack"
        // Released with the launcher: major * 10000 + minor * 100 + patch
        versionCode = 10101
        versionName = "1.1.1"
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}
