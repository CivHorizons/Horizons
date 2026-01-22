pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.0"
    }
    // TODO: Remove this once Detekt 2.0.0-alpha.2 is available
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.detekt") {
                useModule("dev.detekt:detekt-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

rootProject.name = "Civtale"
