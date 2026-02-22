plugins {
    java
    application
    antlr
    checkstyle
    pmd
    id("com.diffplug.spotless") version "8.2.1"
    id("com.github.spotbugs") version "6.4.8"
}

group = "com.davidconneely"
version = "1.0.0-SNAPSHOT"

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
    implementation("org.jline:jline:3.30.6")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    archiveBaseName = "bazlang"
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
    packageName = "com.davidconneely.bazlang.antlr"
    arguments = arguments + listOf("-visitor")
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

// Checkstyle - Google Java Style
checkstyle {
    toolVersion = "13.2.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/antlr/**")
}

// PMD
pmd {
    toolVersion = "7.21.0"
    isConsoleOutput = true
    isIgnoreFailures = true  // Report violations but don't fail build
    ruleSets = emptyList()
    ruleSetFiles = files("config/pmd/ruleset.xml")
}

tasks.withType<Pmd>().configureEach {
    exclude("**/antlr/**")
}

// SpotBugs
spotbugs {
    toolVersion = "4.9.8"
    ignoreFailures = true  // Don't fail build - SpotBugs has issues with Java 25
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter = file("config/spotbugs/exclude.xml")
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}
