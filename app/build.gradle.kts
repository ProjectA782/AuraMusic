import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val baseApplicationId = "com.aura.music"
val applicationIdOverride = System.getenv("AURA_APPLICATION_ID")?.takeIf { it.isNotBlank() }
val appNameOverride = System.getenv("AURA_APP_NAME")?.takeIf { it.isNotBlank() }
// Lets a test build be told it is older than the release already on GitHub, so the update prompt,
// the download and the installer hand-off can be driven on a device without publishing anything.
val versionNameOverride = System.getenv("AURA_VERSION_NAME")?.takeIf { it.isNotBlank() }
val buildCommit =
    System.getenv("AURA_BUILD_COMMIT")
        ?.trim()
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
        ?.take(7)
        ?.lowercase()
val debugKeystorePathOverride = System.getenv("AURA_DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val debugKeystorePassword = System.getenv("AURA_DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
val debugKeyAlias = System.getenv("AURA_DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
val debugKeyPassword = System.getenv("AURA_DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
val persistentDebugKeystoreFile = file("persistent-debug.keystore")
val workflowDebugKeystoreFile = debugKeystorePathOverride?.let(::file)

// The stable line signs with its own key, kept out of git in local.properties the same way the
// Last.fm keys are. Losing it means an installed v1 can never be updated in place: the only way
// in is an uninstall, and that takes the Spotify and Qobuz logins with it.
val releaseKeystoreFile =
    localProperties.getProperty("RELEASE_STORE_FILE")?.let(::file) ?: file("keystore/release.keystore")
val releaseStorePassword =
    localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("STORE_PASSWORD") ?: ""
val releaseKeyAlias =
    localProperties.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("KEY_ALIAS") ?: ""
val releaseKeyPassword =
    localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD") ?: ""
val releaseIsSigned =
    releaseKeystoreFile.exists() && releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() && releaseKeyPassword.isNotBlank()

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.aura.music"
    compileSdk = 37

    defaultConfig {
        applicationId = applicationIdOverride ?: "com.aura.music"
        minSdk = 26
        targetSdk = 36
        // Both builds share this number, so a reinstall within one release still replaces in place.
        // What the updater compares is versionName, but Android will not overwrite an installed
        // package with a different APK of the same versionCode, so every published release moves
        // this number as well: 1 was v1.0.0 and v1.0.1, 2 is v1.0.2, 3 is v1.0.3, 4 is v1.0.4,
        // 5 is v1.0.5 (a throwaway tag for the updater install test).
        versionCode = 5
        versionName = versionNameOverride ?: "1.0.5"
        val baseVersionName = requireNotNull(versionName)
        buildConfigField("String", "BASE_VERSION_NAME", "\"$baseVersionName\"")
        buildCommit?.let { versionName = "$baseVersionName+$it" }
        resValue("string", "app_name", appNameOverride ?: "Aura Music")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        buildConfigField("String", "ARCHITECTURE", "\"universal\"")

        // Crash reporting target: GitHub repo (owner/name) where Issues are created,
        // and a fine-grained PAT with issues:write scoped to that repo only.
        // Both must be present at build time for reporting to be active at runtime.
        val crashRepo = localProperties.getProperty("CRASH_REPORT_REPO")
            ?: System.getenv("CRASH_REPORT_REPO") ?: ""
        val crashToken = localProperties.getProperty("CRASH_REPORT_TOKEN")
            ?: System.getenv("CRASH_REPORT_TOKEN") ?: ""
        buildConfigField("String", "CRASH_REPORT_REPO", "\"$crashRepo\"")
        buildConfigField("String", "CRASH_REPORT_TOKEN", "\"$crashToken\"")
    }

    flavorDimensions += listOf("variant")
    productFlavors {
        // FOSS - Updater build
        create("foss") {
            dimension = "variant"
            isDefault = true
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "true")
        }

        // GMS - kept as a distinct variant id; the Cast code it once carried is gone
        create("gms") {
            dimension = "variant"
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "true")
        }

        // IzzyOnDroid - no updater - the ONLY F-droid compliant build
        create("izzy") {
            dimension = "variant"
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "false")
        }
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = persistentDebugKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("workflowDebug") {
            storeFile = workflowDebugKeystoreFile ?: persistentDebugKeystoreFile
            storePassword = debugKeystorePassword
            keyAlias = debugKeyAlias
            keyPassword = debugKeyPassword
        }
        create("release") {
            storeFile = releaseKeystoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            // Signed locally when the stable key is present. Without it the variant still builds,
            // because the release workflow compiles first and signs in a later step.
            if (releaseIsSigned) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        debug {
            if (applicationIdOverride == null) {
                applicationIdSuffix = ".debug"
            }
            isDebuggable = true
            if (appNameOverride == null) {
                resValue("string", "app_name", "Aura Music Debug")
            }
            signingConfig =
                if (workflowDebugKeystoreFile != null) {
                    signingConfigs.getByName("workflowDebug")
                } else if (persistentDebugKeystoreFile.exists()) {
                    signingConfigs.getByName("persistentDebug")
                } else {
                    signingConfigs.getByName("debug")
                }
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        // Lint never gated anything here (abortOnError = false), so the
        // lintVital pass that assembleRelease implicitly triggers was pure
        // build time. Run lint on demand with ./gradlew :app:lintGmsRelease.
        checkReleaseBuilds = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols +=
                listOf(
                    "**/libandroidx.graphics.path.so",
                    "**/libdatastore_shared_counter.so",
                )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}

val cleanLegacyProtoSources = tasks.register<Delete>("cleanLegacyProtoSources") {
    delete(layout.projectDirectory.dir("src/main/java/com/aura/music/listentogether/proto"))
}

tasks.named("preBuild") {
    dependsOn(cleanLegacyProtoSources)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
        )
        suppressWarnings.set(false)
    }
}

// Android provides org.json as a platform API (/apex/com.android.art/javalib/core-libart.jar).
// The standalone org.json:json artefact bundles an older Apache Harmony copy of JSONArray that
// contains an internal `myArrayList` field absent from the platform class.  Without obfuscation
// R8 inlines against this internal field; at runtime the platform class is resolved instead,
// producing a NoSuchFieldError.  Excluding the artefact globally ensures only the platform
// class is ever referenced.
configurations.configureEach {
    exclude(group = "org.json", module = "json")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)

    implementation(libs.activity)
    implementation(libs.browser)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.browser)

    implementation(libs.ucrop)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.tinypinyin)
    ksp(libs.room.compiler)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    // Musixmatch's provider builds its own client on the CIO engine.
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)

    // Protobuf for message serialization (lite version for Android)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ktor.client.mock)
}
