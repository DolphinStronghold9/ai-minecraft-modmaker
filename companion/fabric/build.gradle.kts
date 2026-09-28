plugins {
    id("fabric-loom") version "1.13.4"
}

version = "1.11.2+26.3"
group = "dev.mortar"

base { archivesName.set("mortar-companion") }

dependencies {
    // From 26.1 the game ships unobfuscated, so no mappings are needed.
    minecraft("com.mojang:minecraft:26.3")
    modImplementation("net.fabricmc:fabric-loader:0.18.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.1+26.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
