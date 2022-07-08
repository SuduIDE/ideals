import org.jetbrains.intellij.tasks.RunIdeTask

plugins {
  id("java")
  // id("org.jetbrains.kotlin.jvm") version "1.7.0"
  id("org.jetbrains.intellij") version "1.6.0"
}

group = "org.rri"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.14.0")
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
  version.set("2022.1")
  type.set("IC") // Target IDE Platform
  pluginsRepositories {
    marketplace()
  }
  plugins.set(listOf("PythonCore:221.5080.216"))
}

open class PlainIdeTask : RunIdeTask()
tasks.register<PlainIdeTask>("plainIdea")

tasks {
  test {
    this.
    jvmArgs = listOf(
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",

      "-Djdk.module.illegalAccess.silent=true"
    )
  }

  getByName("runIde") {
    this as RunIdeTask
    args = listOf("lsp-server")
    systemProperty("java.awt.headless", true)
  }

  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "11"
    targetCompatibility = "11"
  }
/*
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }
*/

  patchPluginXml {
    sinceBuild.set("221")
    untilBuild.set("222.*")
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}
