import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "0.7.3"
    kotlin("jvm") version "1.4.32"
}

group = "zinoviy23"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        setUrl("https://kotlin.bintray.com/kotlinx")
    }
}

val letsPlot = "2.0.2"
val letsPlotApi = "2.0.1"

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-common")
    implementation("org.jetbrains.lets-plot:lets-plot-batik:$letsPlot")
    implementation("org.jetbrains.lets-plot:lets-plot-common:$letsPlot")
    implementation("org.jetbrains.lets-plot:lets-plot-kotlin-api:$letsPlotApi")
    runtimeOnly("org.jetbrains.lets-plot:lets-plot-image-export:$letsPlot")
    testRuntimeOnly("org.jetbrains.lets-plot:lets-plot-image-export:$letsPlot")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    localPath = "/Users/aleksandr.izmaylov/Library/Application Support/JetBrains/Toolbox/apps/IDEA-C/ch-1/212.3340/IntelliJ IDEA 2021.2 CE EAP.app"
//    localSourcesPath = "/Users/aleksandr.izmaylov/NewIDEA"
//    version = "212.3116-EAP-CANDIDATE-SNAPSHOT"
    setPlugins(
        "java",
        "org.jetbrains.kotlin",
        "org.intellij.intelliLang"
    )
    instrumentCode = false
}
tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
    changeNotes("""
      Add change notes here.<br>
      <em>most HTML tags may be used</em>""")
}

listOf("compileKotlin", "compileTestKotlin").forEach {
    tasks.getByName<KotlinCompile>(it) {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.io.path.ExperimentalPathApi"
    }
}

