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

  // JaCoCo coverage verification - one minimum per module, since lib-repl's terminal-wrapping code
  // is inherently far less unit-testable than app-bazlang/lib-cell (see docs/testing.md "What is
  // deliberately not covered"). Each threshold sits a couple of points below that module's actual
  // instruction coverage when this was added (app-bazlang ~72%, lib-cell ~87%, lib-repl ~9%), so
  // normal fluctuation doesn't fail a build - the point is to catch a real regression, not to
  // ratchet coverage upward automatically.
  val minInstructionCoverage =
      when (project.name) {
        "lib-cell" -> 0.85
        "lib-repl" -> 0.05
        else -> 0.70 // app-bazlang
      }

  tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn(tasks.withType<Test>())
    classDirectories.setFrom(
      files(classDirectories.files.map {
        fileTree(it) {
          exclude("**/antlr/**")
        }
      })
    )
    violationRules {
      rule {
        limit {
          counter = "INSTRUCTION"
          minimum = minInstructionCoverage.toBigDecimal()
        }
      }
    }
  }

  tasks.named("check") {
    dependsOn(tasks.withType<JacocoCoverageVerification>())
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

  tasks.named("check") {
    dependsOn(tasks.withType<com.github.spotbugs.snom.SpotBugsTask>())
  }
}
