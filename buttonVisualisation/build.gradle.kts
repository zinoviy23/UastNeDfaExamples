plugins {
    kotlin("jvm") version "1.5.10"
    id("org.jetbrains.intellij") version "0.7.3"
}

group = "zinoviy23"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

val idePath: String by project

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    localPath = idePath
    setPlugins(
        "java",
        "org.jetbrains.kotlin",
        "org.intellij.intelliLang"
    )
    instrumentCode = false
}

listOf("compileKotlin", "compileTestKotlin").forEach {
    tasks.getByName<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>(it) {
        kotlinOptions.languageVersion = "1.5"
        kotlinOptions.apiVersion = "1.5"
        kotlinOptions.jvmTarget = "11"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.io.path.ExperimentalPathApi"
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"
    }
}