package com.github.komarovd95.staledependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.findKotlinCompileTask(sourceSetName: String): KotlinCompile? {
    if (sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME) {
        return tasks.findByName("compileKotlin") as KotlinCompile?
    }
    return tasks.findByName("compile${sourceSetName.capitalize()}Kotlin") as KotlinCompile?
}

fun Project.findCompileClasspathConfiguration(sourceSetName: String): Configuration? {
    if (sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME) {
        return configurations.findByName("compileClasspath")
    }
    return configurations.findByName("${sourceSetName}CompileClasspath")
}

fun Project.findCompileConfigurations(sourceSetName: String): List<Configuration> {
    val configurations = if (sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME) {
        listOf(
                configurations.findByName("compile"),
                configurations.findByName("compileOnly"),
                configurations.findByName("implementation")
        )
    } else {
        listOf(
                configurations.findByName("${sourceSetName}Compile"),
                configurations.findByName("${sourceSetName}CompileOnly"),
                configurations.findByName("${sourceSetName}Implementation")
        )
    }
    return configurations.filterNotNull()
}