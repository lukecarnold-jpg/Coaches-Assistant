plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// The app's data layer: the Room database, the repositories and the backup/CSV formats. It has no
// screens, so a companion app (such as a watch app) can depend on it and share the same storage
// and game logic as the phone app.
android {
    namespace = "com.lukearnold.coachesassistant.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

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

ksp {
    // Exported schemas stay in app/schemas, where the app's migration tests and debug assets read them.
    arg("room.schemaLocation", rootProject.file("app/schemas").path)
}

dependencies {
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (the android.jar only stubs it); see app/build.gradle.kts.
    testImplementation(libs.json)
    // Robolectric gives the Room-backed tests a real SQLite database on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
