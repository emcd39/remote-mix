import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.remotemix.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.remotemix.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 15
        versionName = "0.3.12"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/versions/*/OSGI-INF/MANIFEST.MF",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":adb_kt"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
