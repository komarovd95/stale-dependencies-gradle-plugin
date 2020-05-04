package com.github.komarovd95.staledependencies

data class DeclaredDependency(
        val id: DependencyId,
        val configurationName: String,
        val transitives: Set<DependencyId>
)
