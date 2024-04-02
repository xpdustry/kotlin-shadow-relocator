plugins {
    id("com.diffplug.spotless") version "6.25.0"
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.dokka") version "1.9.20"
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.21.0"
    id("net.kyori.indra") version "3.1.3"
    id("net.kyori.indra.git") version "3.1.3"
    id("net.kyori.indra.publishing.gradle-plugin") version "3.1.3"
    `kotlin-dsl`
}

group = "com.xpdustry"
version = "2.0.0" + if (indraGit.headTag() == null) "-SNAPSHOT" else ""
description = "Gradle plugin for handling Kotlin metadata relocation"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly(gradleApi())
    implementation("com.github.johnrengelman:shadow:8.1.1")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.7.0")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

spotless {
    kotlin {
        ktfmt().dropboxStyle()
        licenseHeader(toLongComment(file("LICENSE_HEADER.md").readText()))
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
    }
}

indra {
    javaVersions {
        target(8)
        minimumToolchain(17)
    }

    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    mitLicense()

    github("xpdustry", "kotlin-shadow-relocator") {
        ci(true)
        issues(true)
        scm(true)
    }

    configurePublications {
        pom {
            organization {
                name.set("Xpdustry")
                url.set("https://www.xpdustry.com")
            }

            developers {
                developer {
                    id.set("Phinner")
                    timezone.set("Europe/Brussels")
                }
            }
        }
    }
}

kotlin {
    explicitApi()
}

indraPluginPublishing {
    website("https://github.com/xpdustry/kotlin-shadow-relocator")

    plugin(
        project.name,
        "com.xpdustry.ksr.KotlinShadowRelocatorPlugin",
        "KSR",
        project.description,
        listOf("kotlin", "shade", "fatjar", "uberjar"),
    )
}

tasks.javadocJar {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml)
}

fun toLongComment(text: String) =
    buildString {
        appendLine("/*")
        text.lines().forEach { appendLine(" * ${it.trim()}") }
        appendLine(" */")
    }
