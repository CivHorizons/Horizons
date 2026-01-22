import dev.detekt.gradle.Detekt

plugins {
    kotlin("jvm") version "2.3.0"
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("dev.detekt") version "main-SNAPSHOT"

    id("run-server")
}

group = findProperty("pluginGroup") as String
version = findProperty("pluginVersion") as String

// TODO: Remove this once Detekt 2.0.0-alpha.2 is available
allprojects {
    repositories {
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // If this does not resolve, run `gradle downloadServer`
    compileOnly(files("run/HytaleServer.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required = true

    }
}
