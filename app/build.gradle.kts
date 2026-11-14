import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: keystore.properties (never committed) or UPDROP_* env vars.
// Without a key the release build falls back to this PC's debug key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
val releaseStore = signing("storeFile", "UPDROP_KEYSTORE_FILE")

android {
    namespace = "app.updrop"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.updrop"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.1"
        // public repo whose latest GitHub Release Updrop installs as its own update
        buildConfigField("String", "GITHUB_REPO", "\"Hytyei/Updrop\"")
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signing("storePassword", "UPDROP_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "UPDROP_KEY_ALIAS")
                keyPassword = signing("keyPassword", "UPDROP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseStore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.animation)
    implementation(libs.coil.compose)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
}
