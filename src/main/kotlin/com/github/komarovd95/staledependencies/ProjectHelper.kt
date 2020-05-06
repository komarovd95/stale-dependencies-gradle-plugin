package com.github.komarovd95.staledependencies

import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.findKotlinCompileTask(sourceSetName: String): TaskProvider<KotlinCompile>? {
    val taskName = if (sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME)
        "compileKotlin"
    else "compile${sourceSetName.capitalize()}Kotlin"

    return try {
        tasks.named(taskName, KotlinCompile::class.java)
    } catch (e: UnknownTaskException) {
        null
    }
}

fun Project.findCompileClasspathConfiguration(sourceSetName: String): Configuration? {
    if (sourceSetName == SourceSet.MAIN_SOURCE_SET_NAME) {
        return configurations.findByName("compileClasspath")
    }
    return configurations.findByName("${sourceSetName}CompileClasspath")
}

fun Project.findKotlinSourceSet(configuration: Configuration): KotlinSourceSet? {
    val kotlinExtension = extensions.getByType(KotlinProjectExtension::class.java)
    return kotlinExtension.sourceSets.firstOrNull {
        configuration.name in it.relatedConfigurationNames
    } ?: configurations.firstOrNull { conf -> configuration in conf.extendsFrom }?.let { conf -> findKotlinSourceSet(conf) }
}
