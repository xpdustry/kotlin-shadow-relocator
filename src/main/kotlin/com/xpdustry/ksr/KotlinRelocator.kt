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

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.metadata.jvm.KmModule
import kotlinx.metadata.jvm.KmPackageParts
import kotlinx.metadata.jvm.KotlinModuleMetadata
import kotlinx.metadata.jvm.UnstableMetadataApi
import org.objectweb.asm.ClassReader

internal class KotlinRelocator(private val delegate: SimpleRelocator) : Relocator by delegate {

    companion object {
        private val relocationPaths: MutableMap<ShadowJar, MutableMap<String, String>> = hashMapOf()
        private val relocationPackages: MutableMap<ShadowJar, MutableMap<String, String>> =
            hashMapOf()

        private fun getRelocationPaths(shadowJar: ShadowJar) =
            relocationPaths.getOrPut(shadowJar, ::hashMapOf)

        private fun getRelocationPackages(shadowJar: ShadowJar) =
            relocationPackages.getOrPut(shadowJar, ::hashMapOf)

        internal fun ShadowJar.storeRelocationPath(pattern: String, destination: String) {
            val newPattern = pattern.replace('.', '/') + "/"
            val taskRelocationPaths = getRelocationPaths(this)
            val intersections = taskRelocationPaths.keys.filter { it.startsWith(newPattern) }
            require(intersections.isEmpty()) {
                "Can't relocate from $pattern to $destination as it clashes with another paths: ${intersections.joinToString()}"
            }
            taskRelocationPaths[newPattern] = destination.replace('.', '/') + "/"
            getRelocationPackages(this)[pattern] = destination
        }

        private fun ShadowJar.patchClass(file: Path) {
            val taskRelocationPaths = getRelocationPaths(this)
            Files.newInputStream(file).use { ins ->
                val cr = ClassReader(ins)
                val cw = PatchedClassWriter(cr, 0, taskRelocationPaths)
                val scanner = MetadataAnnotationScanner(cw, taskRelocationPaths)
                cr.accept(scanner, 0)
                if (scanner.wasPatched || cw.wasPatched) {
                    ins.close()
                    Files.delete(file)
                    Files.write(file, cw.toByteArray())
                }
            }
        }

        @OptIn(UnstableMetadataApi::class)
        private fun ShadowJar.patchKotlinModule(file: Path) {
            val taskRelocationPaths = getRelocationPaths(this)
            val taskRelocationPackages = getRelocationPackages(this)
            Files.newInputStream(file).use { ins ->
                val metadata = KotlinModuleMetadata.read(ins.readBytes())
                val result = KmModule()
                for ((pkg, parts) in metadata.kmModule.packageParts) {
                    result.packageParts[taskRelocationPackages.applyPatch(pkg)] =
                        KmPackageParts(
                            parts.fileFacades.mapTo(
                                mutableListOf(), taskRelocationPaths::applyPatch),
                            parts.multiFileClassParts.entries.associateTo(mutableMapOf()) {
                                (name, facade) ->
                                taskRelocationPackages.applyPatch(name) to
                                    taskRelocationPaths.applyPatch(facade)
                            })
                }
                ins.close()
                Files.delete(file)
                Files.write(file, KotlinModuleMetadata.write(result))
            }
        }

        internal fun patchMetadata(task: ShadowJar) {
            val zip = task.archiveFile.get().asFile.toPath()
            FileSystems.newFileSystem(zip, null as ClassLoader?).use { fs ->
                Files.walk(fs.getPath("/")).forEach { path ->
                    if (Files.isRegularFile(path)) {
                        if (path.name.endsWith(".class")) task.patchClass(path)
                        if (path.name.endsWith(".kotlin_module")) task.patchKotlinModule(path)
                    }
                }
            }
        }
    }
}

internal fun Map<String, String>.applyPatch(value: String): String =
    entries.fold(value) { string, replacement ->
        string.replace(replacement.key, replacement.value)
    }
