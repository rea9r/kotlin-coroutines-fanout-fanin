pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    // Resolve the Kotlin plugin from its Maven Central artifact instead of the
    // plugin-portal marker, so the build works from the local cache.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "org.jetbrains.kotlin") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-coroutines-fanout-fanin"
