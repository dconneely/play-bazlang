plugins {
    java
    application
    antlr
    checkstyle
    pmd
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
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
    antlr(libs.antlr.tool)
    implementation(libs.antlr.runtime)
    implementation(libs.bundles.tamboui)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
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
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/antlr/**")
}

// PMD
pmd {
    toolVersion = libs.versions.pmd.get()
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
    toolVersion = libs.versions.spotbugs.tool.get()
    ignoreFailures = true  // Don't fail build - SpotBugs has issues with Java 25
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter = file("config/spotbugs/exclude.xml")
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}
