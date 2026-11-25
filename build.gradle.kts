plugins {
    java
}

group = "io.github.wintermarkinteractive"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Builds the plugin and copies the jar into the local test server.
tasks.register<Copy>("deployToServer") {
    group = "wintermark"
    description = "Builds the plugin and copies it to ../../server/plugins"
    dependsOn(tasks.jar)
    from(tasks.jar)
    // Fixed name on the server, so a new version replaces the old jar instead of loading twice.
    rename { _ -> "${rootProject.name}.jar" }
    into(layout.projectDirectory.dir("../../server/plugins"))
}
