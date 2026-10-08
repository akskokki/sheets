plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Formatting is checked by `./gradlew check` and applied only by `./gradlew spotlessApply`.
val ktlintRules = mapOf(
    // The style Android Studio's formatter produces, so the IDE and ktlint agree.
    "ktlint_code_style" to "intellij_idea",
    "max_line_length" to "120",
    // Composables are named like types.
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
)

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
}
