# kotlin-shadow-relocator

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/xpdustry/kotlin-shadow-relocator/build.yml?color=00b0b3&label=Build)](https://github.com/xpdustry/kotlin-shadow-relocator/actions/workflows/build.yml)
[![Discord](https://img.shields.io/discord/519293558599974912?color=00b0b3&label=Discord)](https://discord.xpdustry.com)

## Description

A gradle plugin for handling the relocation of kotlin projects, fixing kotlin metadata and module files.

~~Stolen~~ Inspired from the [jetbrains exposed gradle plugin](https://github.com/JetBrains/exposed-intellij-plugin).

> And also [Spliterash/shadow-kotlin-relocate](https://github.com/Spliterash/shadow-kotlin-relocate) for some bug fixes.

## Usage

First, add the xpdustry maven repository to plugin management in your `settings.gradle.kts` file.

```kt
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.xpdustry.com/releases") {
            name = "xpdustry-releases"
            mavenContent { releasesOnly() }
        }
    }
}
```

Then invoke the plugin after [shadow](https://github.com/GradleUp/shadow) in your `build.gradle.kts` file.

```kt
plugins {
    java
    id("com.gradleup.shadow") version "$VERSION"
    id("com.xpdustry.ksr") version "$VERSION"
}
```

Now, you can enjoy the additional `kotlinRelocate` extension method to handle your kotlin libraries.

```kt
import com.xpdustry.ksr.kotlinRelocate

tasks.shadowJar {
    // Popular java json library
    relocate("com.google.gson", "shadow.gson")
    // Very nice configuration library for Kotlin
    kotlinRelocate("com.sksamuel.hoplite", "shadow.hoplite")
}
```

## Limitations

This plugin was designed for kotlin [mindustry](https://github.com/Anuken/Mindustry) and minecraft mods/plugins using an **un-relocated** kotlin stdlib.

I can't guarantee that it will work with other kinds of project.

Also, for kotlin multiplatform projects, the relocation for [optional expectation](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-optional-expectation/)
is not implemented (because I don't know how they work).

If you are familiar with [asm](https://asm.ow2.io/) or kotlin multiplatform internals, feel free to open a PR to help address the issues.

## Support

If you need help, you can talk to the maintainers on the [Xpdustry Discord](https://discord.xpdustry.com) in the `#support` channel.
