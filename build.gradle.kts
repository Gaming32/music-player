plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "1.5.11"
}

group = "io.github.gaming32"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}

val runServer by tasks.registering(JavaExec::class) {
    dependsOn(tasks.jar)

    doFirst {
        file("run/plugins").listFiles(File::isFile)?.forEach(File::delete)
        copy {
            from(tasks.jar.get().outputs.files.singleFile)
            into(file("run/plugins"))
        }
    }

    classpath = configurations.mojangMappedServerRuntime.get()
    mainClass = "org.bukkit.craftbukkit.Main"
    workingDir = file("run")
    standardInput = System.`in`
    args("nogui")
}
