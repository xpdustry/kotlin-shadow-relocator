plugins {
    id("com.diffplug.spotless") version "6.25.0"
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.dokka") version "1.9.20"
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
    id("net.kyori.indra") version "3.1.3"
    id("net.kyori.indra.git") version "3.1.3"
    id("net.kyori.indra.publishing.gradle-plugin") version "3.1.3"
    `kotlin-dsl`
}

group = "com.xpdustry"
version = "3.0.0-beta.1" + if (indraGit.headTag() == null) "-SNAPSHOT" else ""
description = "Gradle plugin handling Kotlin metadata relocation for Shadow"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("metadata-jvm"))
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(gradleApi())
    compileOnly("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:9.0.0-beta12")
    implementation("org.ow2.asm:asm:9.7.1")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

spotless {
    kotlin {
        ktfmt().dropboxStyle()
        licenseHeaderFile(file("HEADER.txt"))
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
        target(17)
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
