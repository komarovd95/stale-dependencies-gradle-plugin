package com.github.komarovd95.staledependencies

import com.github.komarovd95.staledependencies.asm.DependencyClassVisitor
import com.github.komarovd95.staledependencies.reports.StaleDependenciesReporter
import com.github.komarovd95.staledependencies.violations.StaleDependencyViolation
import com.github.komarovd95.staledependencies.violations.TransitiveDependencyUsageViolation
import com.github.komarovd95.staledependencies.violations.UnusedDependencyViolation
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.objectweb.asm.ClassReader
import java.io.File
import java.net.URLClassLoader

@CacheableTask
abstract class CheckStaleDependenciesTask : DefaultTask() {

    companion object {
        private val KOTLIN_DEPENDENCIES = setOf(
                DependencyId("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
                DependencyId("org.jetbrains.kotlin", "kotlin-reflect")
        )
    }

    @get:Incremental
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputDirectory
    abstract val compiledClassesDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val sourceSetName: Property<String>

    @get:Internal
    private val classesDependencies by lazy {
        StaleDependenciesReporter.loadClassesDependencies(reportFile.get().asFile)
    }

    @TaskAction
    fun checkStaleDependencies(inputChanges: InputChanges) {
        project.logger.info("Checking stale dependencies: sourceSet=${sourceSetName.get()}, " +
                "incrementally=${inputChanges.isIncremental}")

        val changes = inputChanges.getFileChanges(compiledClassesDirectory)
                .filter { it.file.name.endsWith(".class")}
        if (changes.isEmpty()) {
            throw StopExecutionException("No incrementally changed classes were found")
        }
        val configuration = project.findCompileClasspathConfiguration(sourceSetName.get())
        if (configuration == null || configuration.state != Configuration.State.RESOLVED) {
            throw StopExecutionException("No configuration for sourceSet '${sourceSetName.get()}' was found")
        }
        val resolvedConfiguration = configuration.resolvedConfiguration

        val compileTask = (project.findKotlinCompileTask(sourceSetName.get())
                ?: throw GradleException("Kotlin compile task not found for sourceSet=${sourceSetName.get()}"))

        val classesFromArtifacts = ArtifactsHelper.classesToArtifacts(resolvedConfiguration.resolvedArtifacts)
        val classLoader = createClassLoader(compileTask.classpath)

        changes.forEach { onCompiledClassChanged(it, classesFromArtifacts, classLoader) }

        val declaredDependencies = declaredDependencies(resolvedConfiguration)
        val violations = checkForStaleDependencyViolations(declaredDependencies, resolvedConfiguration)
        StaleDependenciesReporter.createReportFile(
                reportFile.get().asFile,
                sourceSetName.get(),
                classesDependencies,
                violations
        )
    }

    private fun createClassLoader(classpath: FileCollection): ClassLoader {
        return URLClassLoader(
                (classpath.files + compiledClassesDirectory.get().asFile)
                        .map { it.toURI().toURL() }
                        .toTypedArray(),
                null
        )
    }

    private fun declaredDependencies(resolvedConfiguration: ResolvedConfiguration): Set<DeclaredDependency> {
        val firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        return project.findCompileConfigurations(sourceSetName.get())
                .flatMap { configuration ->
                    val configurationDependencies = configuration.dependencies
                            .filter { it.group != null }
                            .map { DefaultModuleIdentifier.newId(it.group!!, it.name) }
                            .toSet()
                    firstLevelDependencies
                            .filter { configurationDependencies.contains(it.module.id.module) }
                            .map { it.toDeclaredDependency(configuration) }
                            .toSet()
                }
                .toSet()
    }

    private fun checkForStaleDependencyViolations(
            declaredDependencies: Set<DeclaredDependency>,
            resolvedConfiguration: ResolvedConfiguration
    ): List<StaleDependencyViolation> {
        val usedDependencies = classesDependencies
                .flatMap { it.value }
                .toSet()
        val resolvedDependenciesIds = resolvedConfiguration.firstLevelModuleDependencies
                .map { it.toDependencyId() }

        return declaredDependencies
                .filter { it.id !in usedDependencies && !KOTLIN_DEPENDENCIES.contains(it.id) }
                .map {
                    val usedTransitiveDependencies = it.transitives.filter { transitive ->
                        transitive in usedDependencies && !resolvedDependenciesIds.contains(transitive)
                    }
                    if (usedTransitiveDependencies.isEmpty()) {
                        UnusedDependencyViolation(it)
                    } else {
                        TransitiveDependencyUsageViolation(it, usedTransitiveDependencies.toSet())
                    }
                }
    }

    private fun onCompiledClassChanged(
            change: FileChange,
            classesToArtifacts: Map<String, Set<ResolvedArtifact>>,
            classLoader: ClassLoader
    ) {
        when (change.changeType) {
            ChangeType.REMOVED -> { classesDependencies.remove(change.file.toClassName()) }
            else -> { classModified(change.file, classesToArtifacts, classLoader) }
        }
    }

    private fun classModified(
            file: File,
            classesToArtifacts: Map<String, Set<ResolvedArtifact>>,
            classLoader: ClassLoader
    ) {
        val classVisitor = DependencyClassVisitor(classesToArtifacts, classLoader)
        file.inputStream().use {
            ClassReader(it).accept(classVisitor, ClassReader.SKIP_DEBUG)
        }
        val ids = classVisitor.dependencies.map { it.toDependencyId() }
        classesDependencies.compute(file.toClassName()) { _, oldValue ->
            val value = oldValue ?: mutableSetOf()
            value.addAll(ids)
            value
        }
    }

    private fun ResolvedArtifact.toDependencyId(): DependencyId {
        return DependencyId(
                groupId = this.moduleVersion.id.group,
                moduleId = this.moduleVersion.id.name
        )
    }

    private fun File.toClassName(): String {
        val resolvedPath = compiledClassesDirectory.get().asFile.toPath().relativize(this.toPath())
                .toString()
                .replace(File.separatorChar, '.')
        return resolvedPath.substring(0, resolvedPath.length - ".class".length)
    }

    private fun ResolvedDependency.toDependencyId(): DependencyId {
        return DependencyId(
                groupId = this.module.id.group,
                moduleId = this.module.id.name
        )
    }

    private fun ResolvedDependency.toDeclaredDependency(configuration: Configuration): DeclaredDependency {
        return DeclaredDependency(
                id = toDependencyId(),
                configurationName = configuration.name,
                transitives = transitiveDependencies()
        )
    }

    private fun ResolvedDependency.transitiveDependencies(): Set<DependencyId> {
        val transitiveDependencies = mutableSetOf<DependencyId>()
        children.forEach {
            val dependencyId = DependencyId(it.moduleGroup, it.moduleName)
            if (it.module.id != this.module.id && transitiveDependencies.add(dependencyId)) {
                transitiveDependencies.addAll(it.transitiveDependencies())
            }
        }
        return transitiveDependencies
    }
}
