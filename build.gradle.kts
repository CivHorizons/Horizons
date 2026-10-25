import dev.detekt.gradle.Detekt

plugins {
    kotlin("jvm") version "2.3.0"
    id("java-library")
    id("idea")
    id("com.gradleup.shadow") version "9.3.1"
    id("dev.detekt") version "main-SNAPSHOT"

    id("com.azuredoom.hytale-tools") version "1.0.51"
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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

hytaleTools {
    javaVersion = 25
    hytaleVersion = "0.7.4"
    patchline = "release"

    manifestGroup = "CivHorizons"
    modId = "Horizons"
    mainClass = "net.civhorizons.horizons.Horizons"
    modCredits = "SoundTech"
    includesPack = true

    manifestDependencies = ""

    bundleAssetEditorRuntime = false
    disableCurseMaven = true
    disableModtaleResolver = true
    disableModifoldRepo = true
    disablePlaceholderApiMaven = true
    disableHytaleModsInfoMaven = true
    disableHytaleModdingMaven = true
    disableAzureDoomMaven = true
}

configurations.named("hytaleBundledRuntime") { withDependencies { clear() } }

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
