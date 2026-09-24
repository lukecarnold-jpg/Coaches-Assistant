import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

/**
 * versionCode must increase with every release (Google Play rejects an upload that doesn't), so
 * it's the number of commits in the current history, which only grows because master is linear
 * (PRs are rebase-merged). Pass -PversionCode=N to set it explicitly. It needs the full history:
 * in a shallow clone the count is too low, so that's warned about (CI checks out full history).
 */
fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
}.getOrNull()

val appVersionCode: Int = providers.gradleProperty("versionCode").orNull?.toInt()
    ?: run {
        if (gitOutput("rev-parse", "--is-shallow-repository") == "true") {
            logger.warn("Shallow git clone: versionCode is derived from the commit count and will be too low.")
        }
        gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()
            ?: 1.also { logger.warn("Couldn't count git commits; using versionCode 1. Pass -PversionCode=N.") }
    }

android {
    namespace = "com.lukearnold.coachesassistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lukearnold.coachesassistant"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = "1.0"

        val apiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Room's MigrationTestHelper loads each version's exported schema JSON as an asset at
        // "<schemaLocation>/<db qualified name>/<version>.json". Robolectric (used by
        // AppDatabaseMigrationTest) resolves unit test assets from the *debug variant's* merged
        // assets rather than a test-specific set, so this goes on "debug" — exposing app/schemas
        // directly (where `ksp { arg("room.schemaLocation", ...) }` already writes them) avoids
        // keeping a second copy in sync. It never reaches a release build.
        getByName("debug") {
            assets.srcDirs(rootProject.layout.projectDirectory.dir("app/schemas"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.material)
    
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowSizeClass)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    // Real org.json implementation for JVM unit tests: the android.jar used to compile/run
    // `test` sources only stubs org.json (as it does the rest of the Android SDK), so
    // MvpVotesTest needs a working implementation on the unit test classpath.
    testImplementation(libs.json)
    // Robolectric provides a real SQLite implementation so GameRepositoryTest can run an
    // in-memory Room database as a fast JVM unit test instead of an emulator-backed androidTest.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.compose.ui.junit4)
    debugImplementation(libs.compose.ui.manifest)
}
