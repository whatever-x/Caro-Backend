allprojects {
    tasks.register("resolveAllDependencies") {
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { conf ->
                    try {
                        conf.resolve()
                    } catch (e: Exception) {
                        logger.lifecycle("SKIP ${conf.name}: ${e.message}")
                    }
                }
        }
    }
}
