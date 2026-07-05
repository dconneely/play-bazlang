plugins {
  application
  antlr
}

application {
  mainClass = "com.davidconneely.bazlang.MainClass"
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
  antlr(libs.antlr.tool)
  implementation(libs.antlr.runtime)
  implementation(project(":lib-cell"))
  implementation(project(":lib-repl"))
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

tasks.register<JavaExec>("runScaffold") {
  group = "application"
  description = "Runs the BazLangDevelopmentScaffold"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "com.davidconneely.bazlang.program.BazLangDevelopmentScaffold"
  // Allow passing arguments from command line like: ./gradlew runScaffold --args="monster"
  val customArgs = project.findProperty("args") as? String
  if (customArgs != null) {
    args = customArgs.split(" ")
  } else {
    args = listOf("all")
  }
}
