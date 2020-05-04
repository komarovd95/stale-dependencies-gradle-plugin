package com.github.komarovd95.staledependencies.reports

import com.github.komarovd95.staledependencies.DeclaredDependency
import com.github.komarovd95.staledependencies.DependencyId
import com.github.komarovd95.staledependencies.violations.StaleDependencyViolation
import com.github.komarovd95.staledependencies.violations.TransitiveDependencyUsageViolation
import com.github.komarovd95.staledependencies.violations.UnusedDependencyViolation
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object StaleDependenciesReporter {

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    private val transformerFactory = TransformerFactory.newInstance()

    private const val SOURCE_SET_TAG_NAME = "SourceSet"
    private const val DEPENDENCIES_TAG_NAME = "Dependencies"
    private const val CLASS_TAG_NAME = "Class"
    private const val DEPENDENCY_TAG_NAME = "Dependency"
    private const val VIOLATIONS_TAG_NAME = "Violations"
    private const val UNUSED_VIOLATION_TAG_NAME = "UnusedDependency"
    private const val TRANSITIVE_VIOLATION_TAG_NAME = "TransitiveUsageDependency"
    private const val SOURCE_SET_NAME_ATTRIBUTE_NAME = "name"
    private const val CLASS_NAME_ATTRIBUTE_NAME = "className"
    private const val GROUP_ID_ATTRIBUTE_NAME = "groupId"
    private const val MODULE_ID_ATTRIBUTE_NAME = "moduleId"
    private const val CONFIGURATION_NAME_ATTRIBUTE_NAME = "configurationName"

    fun createReportFile(
            file: File,
            sourceSetName: String,
            classesDependencies: Map<String, Set<DependencyId>>,
            violations: List<StaleDependencyViolation>
    ) {
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = documentBuilder.newDocument()
        val sourceSetElement = document.createElement(SOURCE_SET_TAG_NAME)
        sourceSetElement.setAttribute(SOURCE_SET_NAME_ATTRIBUTE_NAME, sourceSetName)

        val dependenciesElement = document.createElement(DEPENDENCIES_TAG_NAME)
        classesDependencies.forEach { (className, dependencies) ->
            val classElement = document.createElement(CLASS_TAG_NAME)
            classElement.setAttribute(CLASS_NAME_ATTRIBUTE_NAME, className)
            dependencies.forEach {
                val dependencyElement = document.createElement(DEPENDENCY_TAG_NAME)
                dependencyElement.setAttribute(GROUP_ID_ATTRIBUTE_NAME, it.groupId)
                dependencyElement.setAttribute(MODULE_ID_ATTRIBUTE_NAME, it.moduleId)
                classElement.appendChild(dependencyElement)
            }
            dependenciesElement.appendChild(classElement)
        }
        sourceSetElement.appendChild(dependenciesElement)

        val violationsElement = document.createElement(VIOLATIONS_TAG_NAME)
        violations.forEach {
            when (it) {
                is UnusedDependencyViolation -> {
                    val violationElement = document.createElement(UNUSED_VIOLATION_TAG_NAME)
                    violationElement.setAttribute(
                            CONFIGURATION_NAME_ATTRIBUTE_NAME,
                            it.declaredDependency.configurationName
                    )
                    violationElement.setAttribute(GROUP_ID_ATTRIBUTE_NAME, it.declaredDependency.id.groupId)
                    violationElement.setAttribute(MODULE_ID_ATTRIBUTE_NAME, it.declaredDependency.id.moduleId)
                    violationsElement.appendChild(violationElement)
                }
                is TransitiveDependencyUsageViolation -> {
                    val violationElement = document.createElement(TRANSITIVE_VIOLATION_TAG_NAME)
                    violationElement.setAttribute(
                            CONFIGURATION_NAME_ATTRIBUTE_NAME,
                            it.declaredDependency.configurationName
                    )
                    violationElement.setAttribute(GROUP_ID_ATTRIBUTE_NAME, it.declaredDependency.id.groupId)
                    violationElement.setAttribute(MODULE_ID_ATTRIBUTE_NAME, it.declaredDependency.id.moduleId)
                    it.usedTransitiveDependencies.forEach { transitive ->
                        val dependencyElement = document.createElement(DEPENDENCY_TAG_NAME)
                        dependencyElement.setAttribute(GROUP_ID_ATTRIBUTE_NAME, transitive.groupId)
                        dependencyElement.setAttribute(MODULE_ID_ATTRIBUTE_NAME, transitive.moduleId)
                        violationElement.appendChild(dependencyElement)
                    }
                    violationsElement.appendChild(violationElement)
                }
            }
        }
        sourceSetElement.appendChild(violationsElement)

        document.appendChild(sourceSetElement)
        document.store(file)
    }

    fun loadClassesDependencies(file: File): MutableMap<String, MutableSet<DependencyId>> {
        return if (file.exists()) {
            val result = mutableMapOf<String, MutableSet<DependencyId>>()
            val xmlDocument = file.parseXmlDocument()
            val dependencies = xmlDocument.getElementsByTagName(DEPENDENCIES_TAG_NAME).item(0).childNodes
            for (i in 0 until dependencies.length) {
                val classEntry = dependencies.item(i)
                val className = classEntry.attributes.getNamedItem(CLASS_NAME_ATTRIBUTE_NAME).textContent
                result[className] = findDependencies(classEntry)
            }
            result
        } else {
            mutableMapOf()
        }
    }

    private fun findDependencies(node: Node): MutableSet<DependencyId> {
        val result = mutableSetOf<DependencyId>()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val dependency = children.item(i)
            val attributes = dependency.attributes
            val groupId = attributes.getNamedItem(GROUP_ID_ATTRIBUTE_NAME).textContent
            val moduleId = attributes.getNamedItem(MODULE_ID_ATTRIBUTE_NAME).textContent
            result.add(DependencyId(groupId, moduleId))
        }
        return result
    }

    fun loadViolations(file: File): List<StaleDependencyViolation> {
        return if (file.exists()) {
            val result = mutableListOf<StaleDependencyViolation>()
            val xmlDocument = file.parseXmlDocument()
            val violations = xmlDocument.getElementsByTagName(VIOLATIONS_TAG_NAME).item(0).childNodes
            for (i in 0 until violations.length) {
                val violation = violations.item(i)
                when (violation.nodeName) {
                    UNUSED_VIOLATION_TAG_NAME -> {
                        val attributes = violation.attributes
                        result.add(UnusedDependencyViolation(
                                declaredDependency = DeclaredDependency(
                                        id = DependencyId(
                                                groupId = attributes.getNamedItem(GROUP_ID_ATTRIBUTE_NAME).textContent,
                                                moduleId = attributes.getNamedItem(MODULE_ID_ATTRIBUTE_NAME).textContent
                                        ),
                                        configurationName = attributes.getNamedItem(CONFIGURATION_NAME_ATTRIBUTE_NAME).textContent,
                                        transitives = emptySet()
                                )
                        ))
                    }
                    TRANSITIVE_VIOLATION_TAG_NAME -> {
                        val usedTransitiveDependencies = mutableSetOf<DependencyId>()
                        val transitives = violation.childNodes
                        for (j in 0 until transitives.length) {
                            val transitive = transitives.item(j)
                            val attributes = transitive.attributes
                            usedTransitiveDependencies.add(DependencyId(
                                    groupId = attributes.getNamedItem(GROUP_ID_ATTRIBUTE_NAME).textContent,
                                    moduleId = attributes.getNamedItem(MODULE_ID_ATTRIBUTE_NAME).textContent
                            ))
                        }

                        val attributes = violation.attributes
                        result.add(TransitiveDependencyUsageViolation(
                                declaredDependency = DeclaredDependency(
                                        id = DependencyId(
                                                groupId = attributes.getNamedItem(GROUP_ID_ATTRIBUTE_NAME).textContent,
                                                moduleId = attributes.getNamedItem(MODULE_ID_ATTRIBUTE_NAME).textContent
                                        ),
                                        configurationName = attributes.getNamedItem(CONFIGURATION_NAME_ATTRIBUTE_NAME).textContent,
                                        transitives = emptySet()
                                ),
                                usedTransitiveDependencies = usedTransitiveDependencies
                        ))
                    }
                }
            }
            result

        } else {
            emptyList()
        }
    }

    private fun Document.store(file: File) {
        val transformer = transformerFactory.newTransformer()
        val output = StreamResult(file)
        val source = DOMSource(this)
        transformer.transform(source, output)
    }

    private fun File.parseXmlDocument(): Document {
        val content = readText()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val inputSource = InputSource(StringReader(content))
        return documentBuilder.parse(inputSource)
    }
}
