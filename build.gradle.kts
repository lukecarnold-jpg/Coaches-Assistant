// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Formatting, checked in CI: `./gradlew spotlessApply` fixes what it can. ktlint's Android Studio
// style, adjusted to how this project is written: trailing commas, hand-chosen line wrapping (so
// the rules that re-wrap lines are off), and whole-package imports allowed in the Compose screens.
val ktlintRules = mapOf(
    "ktlint_code_style" to "android_studio",
    "max_line_length" to "off",
    "ij_kotlin_allow_trailing_comma" to true,
    "ij_kotlin_allow_trailing_comma_on_call_site" to true,
    "ij_kotlin_packages_to_use_import_on_demand" to "java.util.*,androidx.compose.**,com.lonrad.coachesassistant.**",
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_class-signature" to "disabled",
    "ktlint_standard_function-expression-body" to "disabled",
    "ktlint_standard_multiline-expression-wrapping" to "disabled",
    "ktlint_standard_chain-method-continuation" to "disabled",
    "ktlint_standard_argument-list-wrapping" to "disabled",
    "ktlint_standard_parameter-list-wrapping" to "disabled",
    "ktlint_standard_binary-expression-wrapping" to "disabled",
    "ktlint_standard_if-else-wrapping" to "disabled",
    "ktlint_standard_filename" to "disabled",
)

spotless {
    kotlin {
        target("app/src/**/*.kt", "shared/src/**/*.kt", "baselineprofile/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "shared/*.gradle.kts", "baselineprofile/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
}
