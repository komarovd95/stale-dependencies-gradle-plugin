package com.github.komarovd95.staledependencies

import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.stream.Collectors

object ArtifactsHelper {

    private val artifactsToClasses: MutableMap<ResolvedArtifact, MutableSet<String>> = ConcurrentHashMap()

    fun classesToArtifacts(artifacts: Set<ResolvedArtifact>): Map<String, Set<ResolvedArtifact>> {
        val result = mutableMapOf<String, MutableSet<ResolvedArtifact>>()
        artifacts.forEach { artifact ->
            val classes = artifactsToClasses.computeIfAbsent(artifact) {
                if (artifact.extension == "jar") {
                    readClassesFromArtifact(it.file).toMutableSet()
                } else {
                    mutableSetOf()
                }
            }
            classes.forEach {
                result.compute(it) { _, oldValues ->
                    if (oldValues == null) {
                        mutableSetOf(artifact)
                    } else {
                        oldValues.add(artifact)
                        oldValues
                    }
                }
            }
        }
        return result
    }

    private fun readClassesFromArtifact(jarFile: File): Set<String> {
        return JarFile(jarFile).use { jar ->
            jar.stream()
                    .map { it.name }
                    .filter { it.endsWith(".class") }
                    .map { it.substring(0, it.length - ".class".length) }
                    .collect(Collectors.toSet())
        }
    }
}
