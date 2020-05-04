package com.github.komarovd95.staledependencies.violations

import com.github.komarovd95.staledependencies.DeclaredDependency

class UnusedDependencyViolation(val declaredDependency: DeclaredDependency) : StaleDependencyViolation {

    override fun asString(): String {
        return """
            Dependency is unused in compile time and can be removed safely (configuration '${declaredDependency.configurationName}'):
                dependency=${declaredDependency.id.groupId}:${declaredDependency.id.moduleId}
        """.trimIndent()
    }
}
