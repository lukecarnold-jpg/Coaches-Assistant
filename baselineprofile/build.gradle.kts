/*
 * Generates the app's baseline profile (the code Android compiles ahead of time for a faster
 * start and smoother first use) and measures startup with and without it. Runs on a connected
 * device or emulator, not in CI:
 *
 *   ./gradlew :app:generateReleaseBaselineProfile   (writes app/src/release/generated/baselineProfiles)
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.lonrad.coachesassistant.baselineprofile.StartupBenchmark
 *
 * See README, "Baseline profile".
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.lonrad.coachesassistant.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Lets the startup benchmark run on an emulator too (its numbers are only indicative there).
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
