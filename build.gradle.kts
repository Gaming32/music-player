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

    classpath = configurations.mojangMappedServerRuntime.get()
    mainClass = "org.bukkit.craftbukkit.Main"
    workingDir = file("run")
    standardInput = System.`in`
    args("nogui", "--add-plugin", tasks.jar.get().outputs.files.singleFile)
}

//afterEvaluate {
//    println(configurations.mojangMappedServerRuntime.get().files.joinToString(File.pathSeparator))
//}
