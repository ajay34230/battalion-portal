plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "com.vision.litert"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Kept in its own module (no kapt) so Room's annotation processing never has to read LiteRT-LM classes.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.0")
}
