plugins {
  id("checkstyle")
  id("pmd")
  id("jacoco")
  alias(libs.plugins.spotless)
  alias(libs.plugins.spotbugs)
}

val checkstyleVersion = libs.versions.checkstyle.get()
val pmdVersion = libs.versions.pmd.get()
val spotbugsToolVersion = libs.versions.spotbugs.tool.get()

val junitBomProvider = libs.junit.bom
val junitJupiterProvider = libs.junit.jupiter
val junitLauncherProvider = libs.junit.launcher

subprojects {
  apply(plugin = "java")
  apply(plugin = "checkstyle")
  apply(plugin = "pmd")
  apply(plugin = "jacoco")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "com.github.spotbugs")

  repositories {
    mavenCentral()
  }

  dependencies {
    add("testImplementation", platform(junitBomProvider))
    add("testImplementation", junitJupiterProvider)
    add("testRuntimeOnly", junitLauncherProvider)
  }

  configure<JavaPluginExtension> {
    toolchain {
      languageVersion = JavaLanguageVersion.of(25)
    }
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = rootProject.projectDir
  }

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      googleJavaFormat()
      targetExclude("build/generated-src/**")
    }
  }

  // Checkstyle
  configure<CheckstyleExtension> {
    toolVersion = checkstyleVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
  }

  tasks.withType<Checkstyle>().configureEach {
    exclude("**/antlr/**")
  }

  // PMD
  configure<PmdExtension> {
    toolVersion = pmdVersion
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSets = emptyList()
    ruleSetFiles = rootProject.files("config/pmd/ruleset.xml")
  }

  tasks.withType<Pmd>().configureEach {
    exclude("**/antlr/**")
  }

  // JaCoCo
  tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.withType<Test>())
    reports {
      xml.required.set(true)
      html.required.set(true)
      csv.required.set(true)
    }
    classDirectories.setFrom(
      files(classDirectories.files.map {
        fileTree(it) {
          exclude("**/antlr/**")
        }
      })
    )
  }

  tasks.named("check") {
    dependsOn(tasks.withType<JacocoReport>())
  }

  // SpotBugs
  configure<com.github.spotbugs.snom.SpotBugsExtension> {
    toolVersion = spotbugsToolVersion
    ignoreFailures = false
  }

  tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    reports {
      create("html") { required.set(true) }
      create("xml") { required.set(true) }
    }
  }
}
