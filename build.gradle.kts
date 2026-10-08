plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Formatting is checked by `./gradlew check` and applied only by `./gradlew spotlessApply`.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
