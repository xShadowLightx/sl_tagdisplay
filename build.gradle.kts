plugins {
    java
}

group = "io.github.shadowlight"
version = "0.1.0-SNAPSHOT"

val paperTarget = providers.gradleProperty("paperTarget").orElse("26.3").get()
val isLegacyTarget = paperTarget == "1.21.11"
val isModernTarget = Regex("26\\.\\d+").matches(paperTarget)
require(isLegacyTarget || isModernTarget) {
    "paperTarget must be a 26.x release or 1.21.11 (received '$paperTarget')"
}

val paperApiVersion = if (isLegacyTarget) "1.21.11-R0.1-SNAPSHOT" else "$paperTarget.build.+"
val paperApiLevel = if (isLegacyTarget) "1.21" else paperTarget
val javaLevel = if (isLegacyTarget) 21 else 25
val targetClassifier = if (isLegacyTarget) "paper-1.21.11" else "paper-$paperTarget-plus"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaLevel))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaLevel)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            "pluginVersion" to project.version,
            "paperApiLevel" to paperApiLevel,
        )
    }
}

tasks.jar {
    archiveClassifier.set(targetClassifier)
}
