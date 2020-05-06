package com.github.komarovd95.staledependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

open class StaleDependenciesPlugin : Plugin<Project> {

    companion object {
        private const val EXTENSION_NAME = "staleDependencies"
        private const val MASTER_TASK_NAME = "checkStaleDependencies"
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, CheckStaleDependenciesExtension::class.java)
        project.afterEvaluate { prj ->
            val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
                ?: throw StopExecutionException("No Kotlin extension found")
            val registeredTasks = kotlinExtension.sourceSets
                    .filter { sourceSet -> sourceSet.name !in extension.excludedSourceSets }
                    .mapNotNull { sourceSet -> prj.registerTask(sourceSet) }
            prj.tasks.register(MASTER_TASK_NAME, CheckStaleDependenciesReportTask::class.java) {
                it.reportFilesDirectory = prj.file("${prj.buildDir}/stale-dependencies")
                it.dependsOn(registeredTasks)
            }
        }
    }

    private fun Project.registerTask(sourceSet: KotlinSourceSet): TaskProvider<CheckStaleDependenciesTask>? {
        val kotlinCompileTaskProvider = findKotlinCompileTask(sourceSet.name)
        if (kotlinCompileTaskProvider == null) {
            project.logger.error("No task")
            return null
        }
        return tasks.register("check${sourceSet.name.capitalize()}StaleDependencies", CheckStaleDependenciesTask::class.java) {
            it.sourceSetName.set(sourceSet.name)
            it.classpath.set(kotlinCompileTaskProvider.map { t -> t.classpath })
            it.compiledClassesDirectory.set(kotlinCompileTaskProvider.flatMap { t -> t.destinationDirectory })
            it.reportFile.set(file("$buildDir/stale-dependencies/${sourceSet.name}-classes-references.xml"))
            it.dependsOn(kotlinCompileTaskProvider)
        }
    }
}
