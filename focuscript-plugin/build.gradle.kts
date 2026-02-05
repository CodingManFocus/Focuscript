plugins {
    `java`
    kotlin("jvm") version "2.3.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Kotlin compiler (provided at runtime via Paper's plugin.yml libraries)
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")

    implementation(project(":focuscript-api"))
}

tasks.processResources {
    // Embed focuscript-api.jar into plugin resources so the runtime compiler can compile against API-only.
    dependsOn(project(":focuscript-api").tasks.named("jar"))

    from(project(":focuscript-api").tasks.named("jar").map { it.outputs.files.singleFile }) {
        into("") // root of resources
        rename { "focuscript-api.jar" }
    }
}

tasks.jar {
    // Bundle API classes inside the plugin jar (single-jar deployment).
    from(project(":focuscript-api").sourceSets.main.get().output)

    // Name
    archiveBaseName.set("Focuscript")
}
