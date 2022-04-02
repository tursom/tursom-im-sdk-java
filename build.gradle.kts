import cn.tursom.gradle.`ts-coroutine`
import cn.tursom.gradle.`ts-delegation`
import cn.tursom.gradle.`ts-log`
import cn.tursom.gradle.`ts-ws-client`
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

group = "cn.tursom"
version = "1.0-SNAPSHOT"

apply(plugin = "ts-gradle")

plugins {
  java
  kotlin("jvm") version "1.6.10"
  id("com.google.protobuf") version "0.8.18"
  `maven-publish`
}

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
  implementation(kotlin("stdlib"))
  api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.6.0")
  `ts-delegation`
  `ts-ws-client`
  `ts-coroutine`
  `ts-log`
  api(group = "com.google.protobuf", name = "protobuf-java", version = "3.19.4")
  implementation(group = "com.github.ben-manes.caffeine", name = "caffeine", version = "2.9.2")

  testImplementation(group = "junit", name = "junit", version = "4.13.2")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.20.0"
  }
  generatedFilesBaseDir = "$projectDir/src"
  //plugins {
  //  id("grpc") {
  //    artifact = "io.grpc:protoc-gen-grpc-java:1.38.0"
  //  }
  //}
  //generateProtoTasks {
  //  all().forEach {
  //    it.plugins {
  //      id("grpc") {
  //        outputSubDir = "java"
  //      }
  //    }
  //  }
  //}
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
