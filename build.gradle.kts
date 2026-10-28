plugins {
    java
}

group = "com.replex"
version = "0.1.0"

// Version de Paper ciblée (liste : https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml).
val paperVersion = "26.2.build.129-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    // Minecraft 26.x exige Java 25.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // Sans cela, changer la version ne régénère pas plugin.yml.
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("CombatGuard.jar")
}
