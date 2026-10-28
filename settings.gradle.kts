// Si Java 25 manque sur la machine, Gradle le télécharge tout seul (toolchain de build.gradle.kts).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CombatGuard"
