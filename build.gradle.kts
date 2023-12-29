import cn.tursom.gradle.apiTursomServer
import cn.tursom.gradle.ts_encrypt
import cn.tursom.gradle.ts_log
import cn.tursom.gradle.useTursomRepositories

buildscript {
  repositories {
    maven {
      url = uri("https://jmp.mvn.tursom.cn:20080/repository/maven-public/")
    }
  }
  dependencies {
    classpath("cn.tursom:ts-gradle-env:1.1-SNAPSHOT") { isChanging = true }
    classpath("cn.tursom:ts-gradle-repos:1.1-SNAPSHOT") { isChanging = true }
    classpath("cn.tursom:ts-gradle-publish:1.1-SNAPSHOT") { isChanging = true }
  }
  configurations {
    all {
      resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
      resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
  }
}

group = "cn.tursom"
version = "1.1-SNAPSHOT"

apply(plugin = "ts-gradle-env")
apply(plugin = "ts-gradle-repos")
apply(plugin = "ts-gradle-publish")

plugins {
  java
  kotlin("jvm") version "1.9.22"
  id("com.google.protobuf") version "0.9.4"
  `maven-publish`
}

useTursomRepositories()

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
  api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.6.1")
  apiTursomServer("ts-delegation")
  apiTursomServer("ts-ws-client")
  apiTursomServer("ts-coroutine")
  ts_log
  ts_encrypt
  api(group = "com.google.protobuf", name = "protobuf-java", version = "3.25.1")
  // caffeine 2.9.2 is used for java 8
  implementation(group = "com.github.ben-manes.caffeine", name = "caffeine", version = "2.9.2")

  testImplementation(group = "junit", name = "junit", version = "4.13.2")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.25.1"
  }
  //generatedFilesBaseDir = "$projectDir/src"
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

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = "21"
  kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
  //kotlinOptions.useIR = true
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

// skip test
if (project.gradle.startParameter.taskNames.firstOrNull { taskName ->
    taskName.endsWith(":test")
  } == null) {
  tasks.withType<Test> {
    enabled = false
  }
}
