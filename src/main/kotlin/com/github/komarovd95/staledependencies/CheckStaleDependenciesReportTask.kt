package com.github.komarovd95.staledependencies

import com.github.komarovd95.staledependencies.reports.StaleDependenciesReporter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

open class CheckStaleDependenciesReportTask : DefaultTask() {

    @get:Input
    lateinit var reportFiles: List<File>

    @TaskAction
    fun report() {
        val violations = reportFiles.flatMap { StaleDependenciesReporter.loadViolations(it) }
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n") { it.asString() })
        }
    }
}
