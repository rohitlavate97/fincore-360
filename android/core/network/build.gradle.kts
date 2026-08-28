plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.fincore.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:security"))
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
