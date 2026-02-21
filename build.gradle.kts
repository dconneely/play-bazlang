plugins {
    java
    application
    antlr
    id("com.diffplug.spotless") version "8.2.1"
}

group = "com.davidconneely"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "com.davidconneely.bazlang.MainClass"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("org.jline:jline:3.29.0")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.davidconneely.bazlang.MainClass",
            "Class-Path" to configurations.runtimeClasspath.get().files.joinToString(" ") { "lib/${it.name}" }
        )
    }
}

tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("libs/lib"))
}

tasks.build {
    dependsOn("copyDependencies")
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-package", "com.davidconneely.bazlang.antlr")
    outputDirectory = file("${layout.buildDirectory.get()}/generated-src/antlr/main/com/davidconneely/bazlang/antlr")
}

tasks.compileJava {
    dependsOn(tasks.generateGrammarSource)
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
        }
    }
}

spotless {
    java {
        googleJavaFormat()
        targetExclude("build/generated-src/**")
    }
}
