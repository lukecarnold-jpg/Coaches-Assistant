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

/**
 * versionName comes from the latest release tag (vX.Y.Z, e.g. "v1.2.0" -> "1.2.0"). A build of a
 * later commit gets how far past it it is ("1.2.0-dev.5"), so it can't be mistaken for the release.
 * Pass -PversionName=... to set it explicitly; with no release tag yet it's "1.0".
 */
val appVersionName: String = providers.gradleProperty("versionName").orNull
    ?: gitOutput("describe", "--tags", "--match", "v[0-9]*", "--long")
        ?.let { Regex("""^v(.+)-(\d+)-g[0-9a-f]+$""").find(it) }
        ?.let { match ->
            val (release, commitsSince) = match.destructured
            if (commitsSince == "0") release else "$release-dev.$commitsSince"
        }
    ?: "1.0"

/**
 * Release signing, for uploading to Google Play. The keystore and its passwords are never
 * committed: each value is read from a Gradle property (e.g. in ~/.gradle/gradle.properties), an
 * environment variable (as CI sets from its secrets), or the gitignored local.properties. With none
 * set, release builds stay unsigned, as before; with only some set, the build fails rather than
 * silently producing an unsigned release. See README, "Publishing".
 */
fun releaseSigningValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)

val releaseSigningNames = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
val releaseSigning: Map<String, String>? = releaseSigningNames.associateWith { releaseSigningValue(it) }.let { values ->
    val missing = values.filterValues { it.isNullOrBlank() }.keys
    when {
        missing.size == values.size -> null
        missing.isNotEmpty() -> throw GradleException("Release signing is partly configured; also set: ${missing.joinToString()}")
        else -> values.mapValues { it.value!! }
    }
}

/**
 * The app used to be installed as com.example.basketballrotation. Android can't update that install
 * to the new ID, and apps can't read each other's data, so moving to the new app goes through a
 * transition build: `./gradlew installDebug -PlegacyAppId` installs this code *over* the old app
 * (migrating its data like any update), labelled "Old Coaches Assistant", with a note in
 * Settings → Backup. Back up there, then restore in the new app. See README, "Moving from the old app".
 */
val legacyTransitionBuild: Boolean = providers.gradleProperty("legacyAppId").isPresent

android {
    namespace = "com.lukearnold.coachesassistant"
    compileSdk = 37

    defaultConfig {
        applicationId = if (legacyTransitionBuild) "com.example.basketballrotation" else "com.lukearnold.coachesassistant"
        buildConfigField("boolean", "LEGACY_TRANSITION_BUILD", legacyTransitionBuild.toString())
        manifestPlaceholders["appLabel"] = if (legacyTransitionBuild) "@string/app_name_legacy" else "@string/app_name"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        val apiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$apiKey\"")
    }

    signingConfigs {
        releaseSigning?.let { values ->
            create("release") {
                storeFile = rootProject.file(values.getValue("RELEASE_STORE_FILE"))
                storePassword = values.getValue("RELEASE_STORE_PASSWORD")
                keyAlias = values.getValue("RELEASE_KEY_ALIAS")
                keyPassword = values.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
    // Compose UI tests run as JVM unit tests on Robolectric (no emulator), using the
    // ComponentActivity that ui-test-manifest adds to the debug manifest.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.compose.ui.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.compose.ui.junit4)
    debugImplementation(libs.compose.ui.manifest)
}
