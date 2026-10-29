// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // AGP 9 memakai KGP bawaan 2.2.10; naikkan agar sama dengan versi Compose Compiler
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}