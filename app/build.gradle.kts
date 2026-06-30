import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Resolve config from environment variables first, falling back to Gradle
// properties. This keeps secrets out of the repo: CI exports env vars decoded
// from GitHub Actions secrets, while local/contributor builds can omit them.
fun fogwalkConfig(name: String): String? =
    System.getenv(name) ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val fogwalkKeystore = fogwalkConfig("FOGWALK_KEYSTORE")
val fogwalkKeystorePassword = fogwalkConfig("FOGWALK_KEYSTORE_PASSWORD")
val fogwalkKeyAlias = fogwalkConfig("FOGWALK_KEY_ALIAS")
val fogwalkKeyPassword = fogwalkConfig("FOGWALK_KEY_PASSWORD")

// Only use the stable release signing config when all inputs are present and the
// keystore file actually exists; otherwise fall back to debug signing so plain
// `./gradlew assembleRelease` keeps working without secrets.
val hasReleaseSigning = !fogwalkKeystore.isNullOrBlank() &&
    File(fogwalkKeystore).exists() &&
    !fogwalkKeystorePassword.isNullOrBlank() &&
    !fogwalkKeyAlias.isNullOrBlank() &&
    !fogwalkKeyPassword.isNullOrBlank()

// Opt-in strict mode for trusted environments (CI release builds): when set,
// we refuse to silently fall back to debug signing. Local/contributor builds
// leave this unset and keep the convenient debug fallback.
val requireKeystore = fogwalkConfig("FOGWALK_REQUIRE_KEYSTORE")
    ?.lowercase() in setOf("true", "1")

if (requireKeystore && !hasReleaseSigning) {
    throw GradleException(
        "FOGWALK_REQUIRE_KEYSTORE is set but no valid release keystore was " +
            "provided; refusing to fall back to debug signing. Ensure " +
            "FOGWALK_KEYSTORE points to an existing keystore file and that " +
            "FOGWALK_KEYSTORE_PASSWORD, FOGWALK_KEY_ALIAS, and " +
            "FOGWALK_KEY_PASSWORD are all set."
    )
}

val fogwalkVersionCode = fogwalkConfig("FOGWALK_VERSION_CODE")?.toIntOrNull() ?: 1
val fogwalkVersionName = fogwalkConfig("FOGWALK_VERSION_NAME") ?: "1.0"

android {
    namespace = "com.fogwalk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fogwalk"
        minSdk = 24
        targetSdk = 34
        versionCode = fogwalkVersionCode
        versionName = fogwalkVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(fogwalkKeystore!!)
                storePassword = fogwalkKeystorePassword
                keyAlias = fogwalkKeyAlias
                keyPassword = fogwalkKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Use the stable release keystore when configured (CI with secrets),
            // otherwise fall back to the debug key so contributors can build
            // installable APKs locally without any secrets.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.6.1")
}
