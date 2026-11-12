import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}



val coreDir = (project.findProperty("coreDir") as String?)
    ?: rootProject.file("core").absolutePath

// LessScreenDS build identity. CI passes its run number so every published build has a higher
// versionCode than the last; local builds are build 0. The update repository is only set for
// CI release builds, which turns on the in-app updater.
fun lsSetting(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: (project.findProperty(prop) as String?)
val lsBuildNumber = (lsSetting("LS_BUILD_NUMBER", "lsBuildNumber") ?: "0").toInt()
val lsUpdateRepo = lsSetting("LS_UPDATE_REPO", "lsUpdateRepo") ?: ""

android {
    namespace = "com.seedlessds.app"
    compileSdk = 35
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "com.lessscreends.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 1000 + lsBuildNumber
        versionName = "1.0.1-ls.$lsBuildNumber"
        buildConfigField("String", "UPDATE_REPO", "\"$lsUpdateRepo\"")

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DSEEDLESS_CORE_DIR=$coreDir"
                System.getenv("SEEDLESS_THIRD_PARTY_DIR")?.let { arguments += "-DSEEDLESS_THIRD_PARTY_DIR=$it" }
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }



    signingConfigs {
        create("release") {
            val ciStore = System.getenv("LS_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            if (ciStore != null) {
                // CI: the release key comes from repository secrets.
                storeFile = file(ciStore)
                storePassword = System.getenv("LS_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("LS_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "lessscreen"
                keyPassword = System.getenv("LS_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: storePassword
            } else {
                val local = Properties()
                val localFile = rootProject.file("local.properties")
                if (localFile.isFile) localFile.inputStream().use { local.load(it) }
                local.getProperty("MELONDS_KEYSTORE")?.let { storeFile = file(it) }
                storePassword = local.getProperty("MELONDS_KEYSTORE_PASSWORD") ?: ""
                keyAlias = local.getProperty("MELONDS_KEY_ALIAS") ?: ""
                keyPassword = local.getProperty("MELONDS_KEY_PASSWORD") ?: ""
            }
        }
        create("shared") {



            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "FRAME_CONTROLS", (project.findProperty("frameControls") == "true").toString())

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "FRAME_CONTROLS", "true")
            signingConfig = signingConfigs.getByName("shared")
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("com.google.android.material:material:1.12.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
}
