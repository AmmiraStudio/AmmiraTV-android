// Top-level build file for AmmiraTv
// AGP 8.9.2 + Kotlin 2.1.21

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
