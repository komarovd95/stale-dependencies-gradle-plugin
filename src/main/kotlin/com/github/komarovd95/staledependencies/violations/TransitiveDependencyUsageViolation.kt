package com.github.komarovd95.staledependencies.violations

import com.github.komarovd95.staledependencies.DeclaredDependency
import com.github.komarovd95.staledependencies.DependencyId

class TransitiveDependencyUsageViolation(
        val declaredDependency: DeclaredDependency,
        val usedTransitiveDependencies: Set<DependencyId>
) : StaleDependencyViolation {

    override fun asString(): String {
        return """ 
            Dependency is used only for it's transitive dependencies (configuration '${declaredDependency.configurationName}'):
                dependency=${declaredDependency.id.groupId}:${declaredDependency.id.moduleId}
            ${usedTransitiveDependencies.joinToString(separator = "\n") {
                "\t\t used transitive dependency=${it.groupId}:${it.moduleId}"
            }}
        """.trimIndent()
    }
}
