package com.github.komarovd95.staledependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

open class StaleDependenciesPlugin : Plugin<Project> {

    companion object {
        private const val EXTENSION_NAME = "staleDependencies"
        private const val MASTER_TASK_NAME = "checkStaleDependencies"
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, CheckStaleDependenciesExtension::class.java)
        project.afterEvaluate { p ->
            val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
                ?: throw StopExecutionException("No Kotlin extension found")
            val reportFiles = kotlinExtension.sourceSets
                    .filter { sourceSet -> sourceSet.name !in extension.excludedSourceSets }
                    .mapNotNull { sourceSet -> p.createTask(sourceSet) }
            p.tasks.register(MASTER_TASK_NAME, CheckStaleDependenciesReportTask::class.java) {
                it.reportFiles = reportFiles
                it.dependsOn(p.tasks.withType(CheckStaleDependenciesTask::class.java))
            }
        }
    }

    private fun Project.createTask(sourceSet: KotlinSourceSet): File? {
        val kotlinCompileTask = findKotlinCompileTask(sourceSet.name)
        if (kotlinCompileTask == null) {
            project.logger.error("Kotlin compile task not found for sourceSet=${sourceSet.name}")
            return null
        }
        val reportFile = file("$buildDir/stale-dependencies/${sourceSet.name}-classes-references.xml")
        tasks.register("check${sourceSet.name.capitalize()}StaleDependencies", CheckStaleDependenciesTask::class.java) {
            it.sourceSetName.set(sourceSet.name)
            it.compiledClassesDirectory.set(file(kotlinCompileTask.destinationDir))
            it.reportFile.set(reportFile)
            it.dependsOn(kotlinCompileTask)
        }
        return reportFile
    }
}
