plugins {
  java
  application
  antlr
  checkstyle
  pmd
  alias(libs.plugins.spotless)
  alias(libs.plugins.spotbugs)
}

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
  isIgnoreFailures = true
  ruleSets = emptyList()
  ruleSetFiles = files("config/pmd/ruleset.xml")
}

tasks.withType<Pmd>().configureEach {
  exclude("**/antlr/**")
}

// SpotBugs
spotbugs {
  toolVersion = libs.versions.spotbugs.tool.get()
  ignoreFailures = true
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
  excludeFilter.set(file("config/spotbugs/exclude.xml"))
  reports {
    create("html") { required.set(true) }
    create("xml") { required.set(false) }
  }
}
