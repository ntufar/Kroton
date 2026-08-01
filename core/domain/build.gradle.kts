plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    jvm()

    // TODO: enable iOS targets once the KMP native toolchain is validated in CI.
    // iosArm64()
    // iosSimulatorArm64()
    // iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.database)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.github.ntufar.kroton.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}
