plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    jvm()

    // TODO: enable iOS targets once the KMP native toolchain is validated in CI.
    // The XLSX writer will be actual/expect: androidMain uses fastexcel, iOS gets
    // a native implementation later (or CSV/JSON-only parity for v1 of the port).
    // iosArm64()
    // iosSimulatorArm64()
    // iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.github.ntufar.kroton.export"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}
