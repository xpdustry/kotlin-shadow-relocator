/*
 * This file is part of KSR, a gradle plugin for handling Kotlin metadata relocation.
 *
 * MIT License
 *
 * Copyright (c) 2023 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.xpdustry.ksr

import com.github.jengelman.gradle.plugins.shadow.relocation.CacheableRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.metadata.jvm.JvmMetadataVersion
import kotlinx.metadata.jvm.KmModule
import kotlinx.metadata.jvm.KmPackageParts
import kotlinx.metadata.jvm.KotlinModuleMetadata
import kotlinx.metadata.jvm.UnstableMetadataApi
import org.gradle.api.Action
import org.gradle.api.tasks.Internal
import org.objectweb.asm.ClassReader

/**
 * A wrapper around [ShadowJar.relocate] that will also take care of kotlin metadata. Only use it
 * for kotlin libraries. Using it on normal JVM libraries will just increase compilation time.
 */
public fun ShadowJar.kotlinRelocate(
    pattern: String,
    shadedPattern: String,
    configure: Action<SimpleRelocator> = Action {}
) {
    val relocator = KotlinRelocator(pattern, shadedPattern)
    configure.execute(relocator)
    val intersections =
        relocators.filterIsInstance<KotlinRelocator>().filter {
            it.pathPattern.startsWith(relocator.pathPattern)
        }
    require(intersections.isEmpty()) {
        "Can't relocate from $pattern to $shadedPattern as it clashes with another paths: ${intersections.joinToString()}"
    }
    relocate(relocator)
}

internal typealias Relocation = Pair<String, String>

internal typealias RelocationMap = Map<String, String>

internal fun RelocationMap.applyRelocation(value: String): String =
    entries.fold(value) { string, replacement ->
        string.replace(replacement.key, replacement.value)
    }

@CacheableRelocator
internal class KotlinRelocator(pattern: String, shadedPattern: String) :
    SimpleRelocator(pattern, shadedPattern, emptyList(), emptyList()) {

    @get:Internal
    internal val paths: Relocation
        get() = pathPattern to shadedPathPattern

    @get:Internal
    internal val packages: Relocation
        get() = pathPattern to shadedPattern
}

internal fun relocateMetadata(task: ShadowJar) {
    @Suppress("SpellCheckingInspection")
    val relocators = task.relocators.filterIsInstance<KotlinRelocator>()
    val paths = relocators.associate(KotlinRelocator::paths)
    val packages = relocators.associate(KotlinRelocator::packages)
    val zip = task.archiveFile.get().asFile.toPath()
    FileSystems.newFileSystem(zip, null as ClassLoader?).use { fs ->
        Files.walk(fs.getPath("/")).forEach { path ->
            if (!Files.isRegularFile(path)) return@forEach
            if (path.name.endsWith(".class")) relocateClass(path, paths)
            if (path.name.endsWith(".kotlin_module")) relocateKotlinModule(path, paths, packages)
        }
    }
}

private fun relocateClass(file: Path, paths: RelocationMap) {
    Files.newInputStream(file).use { ins ->
        val cr = ClassReader(ins)
        val cw = RelocatingClassWriter(cr, 0, paths)
        val scanner = MetadataAnnotationScanner(cw, paths)
        cr.accept(scanner, 0)
        if (scanner.wasRelocated || cw.wasRelocated) {
            ins.close()
            Files.delete(file)
            Files.write(file, cw.toByteArray())
        }
    }
}

@OptIn(UnstableMetadataApi::class)
private fun relocateKotlinModule(file: Path, paths: RelocationMap, packages: RelocationMap) {
    Files.newInputStream(file).use { ins ->
        val metadata = KotlinModuleMetadata.read(ins.readBytes())
        val result = KmModule()
        for ((pkg, parts) in metadata.kmModule.packageParts) {
            result.packageParts[paths.applyRelocation(pkg)] =
                KmPackageParts(
                    parts.fileFacades.mapTo(mutableListOf(), paths::applyRelocation),
                    parts.multiFileClassParts.entries.associateTo(mutableMapOf()) { (name, facade)
                        ->
                        packages.applyRelocation(name) to paths.applyRelocation(facade)
                    })
        }
        ins.close()
        Files.delete(file)
        Files.write(
            file, KotlinModuleMetadata(result, JvmMetadataVersion.LATEST_STABLE_SUPPORTED).write())
    }
}
