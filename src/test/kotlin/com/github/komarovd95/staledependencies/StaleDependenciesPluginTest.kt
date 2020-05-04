package com.github.komarovd95.staledependencies

import com.github.komarovd95.staledependencies.reports.StaleDependenciesReporter
import com.github.komarovd95.staledependencies.violations.StaleDependencyViolation
import com.github.komarovd95.staledependencies.violations.TransitiveDependencyUsageViolation
import com.github.komarovd95.staledependencies.violations.UnusedDependencyViolation
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StaleDependenciesPluginTest {

    companion object {
        private const val JSON_PATH_DEPENDENCY = "com.jayway.jsonpath:json-path:2.4.0"
        private const val COMMONS_IO_DEPENDENCY = "commons-io:commons-io:2.6"
        private const val JSON_SMART_DEPENDENCY = "net.minidev:json-smart:2.3"
        private const val AWAITILITY_DEPENDENCY = "org.awaitility:awaitility:4.0.2"
        private const val HAMCREST_DEPENDENCY = "org.hamcrest:hamcrest:2.1"
    }

    @Test
    fun `should succeed without any violations`(@TempDir folder: Path) {
        folder.initProject(
            "implementation" to JSON_SMART_DEPENDENCY,
            "testImplementation" to HAMCREST_DEPENDENCY
        )

        val buildResult = GradleRunner.create()
            .withProjectDir(folder.toFile())
            .withArguments("checkStaleDependencies", "-info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val checkMainStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkMainStaleDependencies"
        }
        Assertions.assertNotNull(checkMainStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkMainStaleDependenciesTask?.outcome)

        val checkTestStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkTestStaleDependencies"
        }
        Assertions.assertNotNull(checkTestStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkTestStaleDependenciesTask?.outcome)

        val mainClassReferences = folder.resolve("build/stale-dependencies/main-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(mainClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.A" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.A\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "net.minidev", moduleId = "json-smart")
                ),
                "com.github.komarovd95.staledependencies.testproject.B" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.B\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(mainClassReferences.toFile())
        )

        val testClassReferences = folder.resolve("build/stale-dependencies/test-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(testClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.SimpleTest" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.SimpleTest\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "org.hamcrest", moduleId = "hamcrest")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(testClassReferences.toFile())
        )
    }

    @Test
    fun `should failed with unused violation`(@TempDir folder: Path) {
        folder.initProject(
            "implementation" to JSON_SMART_DEPENDENCY,
            "implementation" to COMMONS_IO_DEPENDENCY,
            "testImplementation" to HAMCREST_DEPENDENCY
        )

        val buildResult = GradleRunner.create()
            .withProjectDir(folder.toFile())
            .withArguments("checkStaleDependencies", "-info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()

        val checkMainStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkMainStaleDependencies"
        }
        Assertions.assertNotNull(checkMainStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkMainStaleDependenciesTask?.outcome)

        val checkTestStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkTestStaleDependencies"
        }
        Assertions.assertNotNull(checkTestStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkTestStaleDependenciesTask?.outcome)

        val mainClassReferences = folder.resolve("build/stale-dependencies/main-classes-references.xml")
        val mainViolations = StaleDependenciesReporter.loadViolations(mainClassReferences.toFile())
        Assertions.assertEquals(1, mainViolations.size)
        Assertions.assertTrue(mainViolations[0] is UnusedDependencyViolation)
        with(mainViolations[0] as UnusedDependencyViolation) {
            Assertions.assertEquals("implementation", this.declaredDependency.configurationName)
            Assertions.assertEquals("commons-io", this.declaredDependency.id.groupId)
            Assertions.assertEquals("commons-io", this.declaredDependency.id.moduleId)
        }
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.A" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.A\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "net.minidev", moduleId = "json-smart")
                ),
                "com.github.komarovd95.staledependencies.testproject.B" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.B\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(mainClassReferences.toFile())
        )

        val testClassReferences = folder.resolve("build/stale-dependencies/test-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(testClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.SimpleTest" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.SimpleTest\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "org.hamcrest", moduleId = "hamcrest")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(testClassReferences.toFile())
        )
    }

    @Test
    fun `should failed with transitive usage violation`(@TempDir folder: Path) {
        folder.initProject(
            "implementation" to JSON_PATH_DEPENDENCY,
            "testImplementation" to AWAITILITY_DEPENDENCY
        )

        val buildResult = GradleRunner.create()
            .withProjectDir(folder.toFile())
            .withArguments("checkStaleDependencies", "-info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()

        val checkMainStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkMainStaleDependencies"
        }
        Assertions.assertNotNull(checkMainStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkMainStaleDependenciesTask?.outcome)

        val checkTestStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkTestStaleDependencies"
        }
        Assertions.assertNotNull(checkTestStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkTestStaleDependenciesTask?.outcome)

        val mainClassReferences = folder.resolve("build/stale-dependencies/main-classes-references.xml")
        val mainViolations = StaleDependenciesReporter.loadViolations(mainClassReferences.toFile())
        Assertions.assertEquals(1, mainViolations.size)
        Assertions.assertTrue(mainViolations[0] is TransitiveDependencyUsageViolation)
        with(mainViolations[0] as TransitiveDependencyUsageViolation) {
            Assertions.assertEquals("implementation", this.declaredDependency.configurationName)
            Assertions.assertEquals("com.jayway.jsonpath", this.declaredDependency.id.groupId)
            Assertions.assertEquals("json-path", this.declaredDependency.id.moduleId)
            Assertions.assertEquals(
                setOf(DependencyId(groupId = "net.minidev", moduleId = "json-smart")),
                this.usedTransitiveDependencies
            )
        }
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.A" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.A\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "net.minidev", moduleId = "json-smart")
                ),
                "com.github.komarovd95.staledependencies.testproject.B" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.B\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(mainClassReferences.toFile())
        )

        val testClassReferences = folder.resolve("build/stale-dependencies/test-classes-references.xml")
        val testViolations = StaleDependenciesReporter.loadViolations(testClassReferences.toFile())
        Assertions.assertEquals(1, testViolations.size)
        Assertions.assertTrue(testViolations[0] is TransitiveDependencyUsageViolation)
        with(testViolations[0] as TransitiveDependencyUsageViolation) {
            Assertions.assertEquals("testImplementation", this.declaredDependency.configurationName)
            Assertions.assertEquals("org.awaitility", this.declaredDependency.id.groupId)
            Assertions.assertEquals("awaitility", this.declaredDependency.id.moduleId)
            Assertions.assertEquals(
                setOf(DependencyId(groupId = "org.hamcrest", moduleId = "hamcrest")),
                this.usedTransitiveDependencies
            )
        }
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.SimpleTest" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.SimpleTest\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "org.hamcrest", moduleId = "hamcrest")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(testClassReferences.toFile())
        )
    }

    @Test
    fun `should succeed with transitive usage in excluded source set`(@TempDir folder: Path) {
        folder.initProject(
            "implementation" to JSON_SMART_DEPENDENCY,
            "testImplementation" to AWAITILITY_DEPENDENCY
        ).withExcludedSourceSets("test")

        val buildResult = GradleRunner.create()
            .withProjectDir(folder.toFile())
            .withArguments("checkStaleDependencies", "-info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val checkMainStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkMainStaleDependencies"
        }
        Assertions.assertNotNull(checkMainStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkMainStaleDependenciesTask?.outcome)

        val checkTestStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkTestStaleDependencies"
        }
        Assertions.assertNull(checkTestStaleDependenciesTask)

        val mainClassReferences = folder.resolve("build/stale-dependencies/main-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(mainClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.A" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.A\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "net.minidev", moduleId = "json-smart")
                ),
                "com.github.komarovd95.staledependencies.testproject.B" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.B\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(mainClassReferences.toFile())
        )
    }

    @Test
    fun `should failed with unused dependency when source file is removed`(@TempDir folder: Path) {
        folder.initProject(
            "implementation" to JSON_SMART_DEPENDENCY,
            "testImplementation" to HAMCREST_DEPENDENCY
        )

        val buildResult = GradleRunner.create()
            .withProjectDir(folder.toFile())
            .withArguments("checkStaleDependencies", "-info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val checkMainStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkMainStaleDependencies"
        }
        Assertions.assertNotNull(checkMainStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkMainStaleDependenciesTask?.outcome)

        val checkTestStaleDependenciesTask = buildResult.tasks.find {
            it.path == ":checkTestStaleDependencies"
        }
        Assertions.assertNotNull(checkTestStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, checkTestStaleDependenciesTask?.outcome)

        val mainClassReferences = folder.resolve("build/stale-dependencies/main-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(mainClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.A" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.A\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "net.minidev", moduleId = "json-smart")
                ),
                "com.github.komarovd95.staledependencies.testproject.B" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.B\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(mainClassReferences.toFile())
        )

        val testClassReferences = folder.resolve("build/stale-dependencies/test-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(testClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.SimpleTest" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.SimpleTest\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "org.hamcrest", moduleId = "hamcrest")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(testClassReferences.toFile())
        )

        Assertions.assertTrue(
            Files.deleteIfExists(folder.resolve("src/main/kotlin/com/github/komarovd95/staledependencies/testproject/A.kt"))
        )

        val incrementalBuildResult = GradleRunner.create()
            .withProjectDir(folder.toFile())
            .withArguments("checkStaleDependencies", "-info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()

        val incrementalCheckMainStaleDependenciesTask = incrementalBuildResult.tasks.find {
            it.path == ":checkMainStaleDependencies"
        }
        Assertions.assertNotNull(incrementalCheckMainStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.SUCCESS, incrementalCheckMainStaleDependenciesTask?.outcome)

        val incrementalCheckTestStaleDependenciesTask = incrementalBuildResult.tasks.find {
            it.path == ":checkTestStaleDependencies"
        }
        Assertions.assertNotNull(incrementalCheckTestStaleDependenciesTask)
        Assertions.assertEquals(TaskOutcome.UP_TO_DATE, incrementalCheckTestStaleDependenciesTask?.outcome)

        val incrementalMainClassReferences = folder.resolve("build/stale-dependencies/main-classes-references.xml")
        val incrementalMainViolations = StaleDependenciesReporter.loadViolations(incrementalMainClassReferences.toFile())
        Assertions.assertEquals(1, incrementalMainViolations.size)
        Assertions.assertTrue(incrementalMainViolations[0] is UnusedDependencyViolation)
        with(incrementalMainViolations[0] as UnusedDependencyViolation) {
            Assertions.assertEquals("implementation", this.declaredDependency.configurationName)
            Assertions.assertEquals("net.minidev", this.declaredDependency.id.groupId)
            Assertions.assertEquals("json-smart", this.declaredDependency.id.moduleId)
        }
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.B" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.B\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(incrementalMainClassReferences.toFile())
        )

        val incrementalTestClassReferences = folder.resolve("build/stale-dependencies/test-classes-references.xml")
        Assertions.assertEquals(
            emptyList<StaleDependencyViolation>(),
            StaleDependenciesReporter.loadViolations(incrementalTestClassReferences.toFile())
        )
        Assertions.assertEquals(
            mapOf(
                "com.github.komarovd95.staledependencies.testproject.SimpleTest" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations")
                ),
                "com.github.komarovd95.staledependencies.testproject.SimpleTest\$Companion" to setOf(
                    DependencyId(groupId = "org.jetbrains.kotlin", moduleId = "kotlin-stdlib"),
                    DependencyId(groupId = "org.jetbrains", moduleId = "annotations"),
                    DependencyId(groupId = "org.hamcrest", moduleId = "hamcrest")
                )
            ),
            StaleDependenciesReporter.loadClassesDependencies(incrementalTestClassReferences.toFile())
        )
    }

    private fun Path.initProject(vararg dependencies: Pair<String, String>): Path {
        val buildFile = Files.createFile(this.resolve("build.gradle"))
        buildFile.toFile().writeText("""
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.3.71'
                id 'com.github.komarovd95.stale-dependencies-plugin'
            }
            repositories {
                mavenCentral()
                jcenter()
            }
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8'
            ${dependencies.joinToString(separator = "\n") { "\t${it.first} '${it.second}'" }}
            }
            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            
        """.trimIndent())

        val mainSourceDirectory = Files.createDirectories(
            this.resolve("src/main/kotlin/com/github/komarovd95/staledependencies/testproject")
        )
        val aClass = Files.createFile(mainSourceDirectory.resolve("A.kt"))
        aClass.toFile().writeText("""
            package com.github.komarovd95.staledependencies.testproject
            
            import net.minidev.json.JSONObject
            
            class A {
                
                companion object {
                    @JvmStatic
                    fun main(args: Array<String>) {
                        println(JSONObject())
                    }
                }
            }
        """.trimIndent())

        val bClass = Files.createFile(mainSourceDirectory.resolve("B.kt"))
        bClass.toFile().writeText("""
            package com.github.komarovd95.staledependencies.testproject
            
            class B {
                
                companion object {
                    @JvmStatic
                    fun main(args: Array<String>) {
                        println("Hello, world!")
                    }
                }
            }
        """.trimIndent())



        val testSourceDirectory = Files.createDirectories(
            this.resolve("src/test/kotlin/com/github/komarovd95/staledependencies/testproject")
        )
        val testFile = Files.createFile(testSourceDirectory.resolve("SimpleTest.kt"))
        testFile.toFile().writeText("""
            package com.github.komarovd95.staledependencies.testproject
            
            import org.hamcrest.CoreMatchers
            import org.hamcrest.MatcherAssert
            
            class SimpleTest {
                
                companion object {
                    @JvmStatic
                    fun main(args: Array<String>) {
                        MatcherAssert.assertThat("Hello", CoreMatchers.equalTo("Hello"))
                    }
                }
            }
        """.trimIndent())

        return this
    }

    private fun Path.withExcludedSourceSets(vararg excludedSourceSets: String) {
        val buildFile = this.resolve("build.gradle").toFile()
        if (buildFile.exists()) {
            buildFile.appendText("""
                staleDependencies {
                    staleDependencies {
                        excludedSourceSets = [${excludedSourceSets.joinToString(",") { "\"$it\"" }}]
                    }
                }
            """.trimIndent())
        }
    }
}
