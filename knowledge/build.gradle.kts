import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.maven.publish)
}

val mavenGroupId: String by project
val mavenArtifactId: String by project
val mavenVersion: String by project

group = mavenGroupId

version = mavenVersion

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.ohs.fhir.knowledge"
    compileSdk = 36
    minSdk = 26
    withHostTestBuilder {}
  }

  jvm("desktop")

  // iosX64 is omitted because androidx.room3 does not publish it; including it breaks dependency
  // resolution. kotlin-fhir-engine drops it for the same reason.
  iosArm64()
  iosSimulatorArm64()

  // useEsModules() is required so that the SQLite-WASM Web Worker, loaded via
  // `new Worker(new URL(..., import.meta.url), { type: "module" })`, resolves as an ES module.
  js {
    browser()
    useEsModules()
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    useEsModules()
  }

  // Downloading and unpacking a package needs gzip, which the web targets do not implement. This
  // group gives the targets that do one place for that code and its tests.
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
    common {
      group("nonWeb") {
        withAndroidTarget()
        withIos()
        withJvm()
      }
    }
  }

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.time.ExperimentalTime")
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.androidx.room3.runtime)
        implementation(libs.kermit)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.core)
        implementation(libs.ohs.fhir.model)
        api(libs.okio)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotest.assertions.core)
        implementation(libs.kotlin.test)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.ktor.client.mock)
        implementation(libs.okio.fakefilesystem)
      }
    }
    val androidMain by getting {
      // withAndroidTarget() does not match the AGP multiplatform library target, so the group has
      // to be joined by hand.
      dependsOn(get("nonWebMain"))
    }
    androidMain.dependencies {
      implementation(libs.androidx.sqlite.bundled)
      implementation(libs.ktor.client.okhttp)
    }
    val desktopMain by getting {
      dependencies {
        implementation(libs.androidx.sqlite.bundled)
        implementation(libs.ktor.client.java)
      }
    }
    getByName("androidHostTest") {
      dependsOn(get("nonWebTest"))
      // The Android artifact of the bundled driver carries device binaries only, so a host JVM
      // cannot open a connection with it. Its jvm build puts a loadable one on the test classpath.
      dependencies { implementation(libs.androidx.sqlite.bundled.jvm) }
    }
    iosMain.dependencies {
      implementation(libs.androidx.sqlite.bundled)
      implementation(libs.ktor.client.darwin)
    }
    webMain.dependencies {
      // Exposes WebWorkerSQLiteDriver to the actual in Platform.kt.
      api(libs.androidx.sqlite.web)
      implementation(libs.kotlinx.browser)
      implementation(libs.ktor.client.js)
      // Local npm module: worker.js and @sqlite.org/sqlite-wasm (see src/webMain/npm).
      implementation(
        npm(
          "sqlite-wasm-worker",
          layout.projectDirectory.dir("src/webMain/npm/sqlite-wasm-worker").asFile,
        )
      )
    }
  }
}

dependencies {
  listOf("kspAndroid", "kspDesktop", "kspIosArm64", "kspIosSimulatorArm64", "kspJs", "kspWasmJs")
    .forEach { add(it, libs.androidx.room3.compiler) }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

mavenPublishing {
  publishToMavenCentral()
  if (
    listOf("signingInMemoryKey", "signing.keyId", "signing.gnupg.keyName").any {
      providers.gradleProperty(it).isPresent
    }
  ) {
    signAllPublications()
  }
  coordinates(mavenGroupId, mavenArtifactId, mavenVersion)

  pom {
    name = "Kotlin FHIR Knowledge Manager"
    description =
      "A Kotlin Multiplatform library for installing FHIR implementation guides from NPM " +
        "packages and resolving the knowledge artifacts they hold by canonical URL"
    inceptionYear = "2026"
    url = "https://github.com/ohs-foundation/kotlin-fhir-knowledge"
    licenses {
      license {
        name = "The Apache License, Version 2.0"
        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
      }
    }
    developers {
      developer {
        id = "ohs-foundation"
        name = "Open Health Stack Foundation"
        url = "https://ohs.dev/"
      }
    }
    scm {
      url = "https://github.com/ohs-foundation/kotlin-fhir-knowledge/"
      connection = "scm:git:git://github.com/ohs-foundation/kotlin-fhir-knowledge.git"
      developerConnection = "scm:git:ssh://git@github.com/ohs-foundation/kotlin-fhir-knowledge.git"
    }
  }
}
