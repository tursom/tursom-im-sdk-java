import com.google.protobuf.gradle.*

buildscript {
  repositories {
    maven {
      url = uri("https://nvm.tursom.cn/repository/maven-public/")
    }
  }
  dependencies {
    classpath("cn.tursom:ts-gradle:1.0-SNAPSHOT") { isChanging = true }
  }
  configurations {
    all {
      resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
      resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
  }
}

apply(plugin = "ts-gradle")

plugins {
  java
  kotlin("jvm") version "1.6.10"
  id("com.google.protobuf") version "0.8.16"
  `maven-publish`
}

group = "cn.tursom"
version = "1.0-SNAPSHOT"

repositories {
  // mavenLocal()
  // mavenCentral()
  maven {
    url = uri("https://nvm.tursom.cn/repository/maven-public/")
  }
}

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
  all {
    resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
    resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
  }
}

dependencies {
  val tursomServerVersion = "1.0-SNAPSHOT"

  implementation(kotlin("stdlib"))
  api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.6.0")
  api(group = "cn.tursom", name = "ts-delegation", version = tursomServerVersion)
  api(group = "cn.tursom", name = "ts-ws-client", version = tursomServerVersion)
  api(group = "cn.tursom", name = "ts-coroutine", version = tursomServerVersion)
  implementation(group = "cn.tursom", name = "ts-log", version = tursomServerVersion)
  api(group = "com.google.protobuf", name = "protobuf-java", version = "3.17.3")
  implementation(group = "com.github.ben-manes.caffeine", name = "caffeine", version = "2.9.2")

  testImplementation(group = "junit", name = "junit", version = "4.12")
}

// artifacts {
//   archives(tasks["kotlinSourcesJar"])
// }

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.17.1"
  }
  generatedFilesBaseDir = "$projectDir/src"
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:1.38.0"
    }
  }
  generateProtoTasks {
    all().forEach {
      it.plugins {
        id("grpc") {
          outputSubDir = "java"
        }
      }
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = "1.8"
  kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
  //kotlinOptions.useIR = true
}

// skip test
if (project.gradle.startParameter.taskNames.firstOrNull { taskName ->
    taskName.endsWith(":test")
  } == null) {
  tasks.withType<Test> {
    enabled = false
  }
}

// tasks.register("install") {
//   // dependsOn(tasks["build"])
//   finalizedBy(tasks["publishToMavenLocal"])
// }

// publishing {
//   publish(this)
// }

// dependencyManagement {
//   resolutionStrategy {
//     cacheChangingModulesFor(0, TimeUnit.SECONDS)
//   }
// }
