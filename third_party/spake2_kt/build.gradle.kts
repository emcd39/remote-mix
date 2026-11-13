plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0") }
