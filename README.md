# Kotlin stale dependencies Gradle plugin

A simple Gradle plugin to check stale dependencies in your Kotlin project. It has 2 types of rules:
* `Unused dependency` - dependency is unused in compile time and can be removed safely (or moved to `runtime*` 
   to configurations)
* `Transitive used dependency` - dependency is used only for its transitive dependencies. Its better way to use this 
   transitive dependencies explicitly (add it to `compile*` configuration)
   
## Implementation notes

This plugin uses ASM to visit compiled Kotlin code so all `check*StaleDependencies` tasks depend on Kotlin compile tasks.

This plugin contains master-task `checkStaleDependencies` that depends on all `check*StaleDependencies` tasks. 
You should use this task if you want to fail your build in case of rules violations.

All `check*StaleDependencies` tasks support caching and incremental builds.
   