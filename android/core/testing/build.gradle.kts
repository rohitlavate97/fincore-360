plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fincore.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.junit.jupiter)
    implementation(libs.mockk)
    implementation(libs.coroutines.test)
    implementation(libs.kotlinx.coroutines.test)
}
