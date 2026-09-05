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
val jacocoToolVersion = libs.versions.jacoco.tool.get()
val findsecbugsPluginProvider = libs.findsecbugs.plugin

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

  // JaCoCo - toolVersion pinned rather than left to the plugin's own default: a Gradle upgrade
  // shouldn't be able to silently change the coverage tool underneath the build.
  configure<JacocoPluginExtension> {
    toolVersion = jacocoToolVersion
  }

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

  // find-sec-bugs - security-focused SpotBugs ruleset.
  dependencies.add("spotbugsPlugins", findsecbugsPluginProvider)

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

  // Javadoc/doclint, unconditional on every subproject: lib-cell/lib-repl are consumed across
  // module boundaries and app-bazlang's MCP server is a programmatic surface other tools call
  // into, so undocumented public API is a real cost here, not just style.
  tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    // Generated ANTLR sources (BazLangLexer/Parser/Listener/Visitor/Base*) are regenerated every
    // build and not ours to document - excluded by filename rather than by package, since the
    // hand-written AntlrParser.java lives in that same com.davidconneely.bazlang.antlr package and
    // does need documenting (unlike Checkstyle/PMD's own "**/antlr/**" exclude, which is fine
    // sweeping AntlrParser.java out too since those tools lint code style, not API completeness).
    exclude(
        "**/BazLangLexer.java",
        "**/BazLangParser.java",
        "**/BazLangListener.java",
        "**/BazLangVisitor.java",
        "**/BazLangBaseListener.java",
        "**/BazLangBaseVisitor.java")
    (options as StandardJavadocDocletOptions).apply {
      addBooleanOption("Xdoclint:all", true)
      addBooleanOption("Xwerror", true)
      // javac/javadoc's default -Xmaxwarns is 100: with Xwerror active that silently truncates the
      // reported list rather than the actual violation count. Set high enough that a real
      // regression is never hidden by the cap.
      addStringOption("Xmaxwarns", "10000")
    }
  }
}
