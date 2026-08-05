plugins {
  java
  application
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

sourceSets {
  main {
    java.setSrcDirs(listOf(
      layout.projectDirectory.dir("generated-src/main/java"),
      layout.projectDirectory.dir("harness/src/main/java")
    ))
  }
  test {
    java.setSrcDirs(listOf(layout.projectDirectory.dir("harness/src/test/java")))
  }
}

application {
  mainClass.set("io.github.mzmine.oracle.V408DifferentialReportMain")
}

dependencies {
  implementation("org.jetbrains:annotations:22.0.0")
  implementation("com.google.guava:guava:33.0.0-jre")
  implementation("it.unimi.dsi:fastutil:8.5.6")
  implementation("io.github.msdk:msdk-io-mzml:0.0.27")
  implementation("commons-io:commons-io:2.15.1")
  implementation("org.apache.commons:commons-lang3:3.0")
  implementation("com.fasterxml:aalto-xml:1.3.2")
  implementation("com.fasterxml.woodstox:woodstox-core:6.6.0")
  testImplementation(platform("org.junit:junit-bom:5.10.2"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.release.set(21)
  options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:all"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
  jvmArgs("--enable-preview")
}

tasks.register("printCompileClasspath") {
  doLast {
    configurations.compileClasspath.get().resolvedConfiguration.resolvedArtifacts
      .sortedBy { it.moduleVersion.id.toString() }
      .forEach { println("${it.moduleVersion.id} -> ${it.file.name}") }
  }
}
