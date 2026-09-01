import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.premnirmal.gradle.getCommitsBetween
import com.github.premnirmal.gradle.getOldGitVersionFromGit
import com.github.premnirmal.gradle.getVersionNameFromGit

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.com.google.devtools.ksp)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
  id("kotlin-parcelize")
  alias(libs.plugins.detekt.plugin)
}

room {
  schemaDirectory("$projectDir/schemas")
}

repositories {
  google()
  mavenCentral()
  maven("https://jitpack.io")
}

// Generates the "what's new" changelog from the local git history into a commonMain constant, so the
// changelog is shared by every platform (Android and iOS) instead of living only in :app's Android
// BuildConfig. CommitsProvider defaults to this constant.
val generatedChangelogDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/changelog/commonMain/kotlin")

val generateChangelog by tasks.registering {
  val outputDir = generatedChangelogDir
  outputs.dir(outputDir)
  val versionName = project.getVersionNameFromGit()
  val oldVersion = project.getOldGitVersionFromGit()
  // git log subjects already have their newlines escaped to the literal "\n" sequence.
  val changeLog = project.getCommitsBetween(old = oldVersion, new = versionName)
  inputs.property("changeLog", changeLog)
  doLast {
    // Escape the characters that would break a Kotlin string literal. Backslashes are intentionally
    // preserved so the embedded "\n" line separators survive as real newlines at runtime.
    val escaped = changeLog
        .replace("\"", "\\\"")
        .replace("\$", "\\\$")
    val pkgDir = outputDir.get().asFile.resolve("com/github/premnirmal/shared")
    pkgDir.mkdirs()
    pkgDir.resolve("ChangelogBuildConfig.kt").writeText(
        """
        |package com.github.premnirmal.shared
        |
        |/**
        | * Generated at build time from the local git history (see :shared `generateChangelog`).
        | * Shared by every platform so Android and iOS show the same "what's new" changelog.
        | */
        |internal object ChangelogBuildConfig {
        |    const val CHANGE_LOG: String = "$escaped"
        |}
        |
        """.trimMargin()
    )
  }
}

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
    // kotlin-parcelize only recognises `kotlinx.parcelize.Parcelize` by default. Our shared
    // models are annotated with the multiplatform `@CommonParcelize` alias, so we register it
    // as an additional Parcelize annotation on the Android compilations (where the plugin runs)
    // to ensure the real Parcelable implementations are generated.
    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          freeCompilerArgs.addAll(
              "-P",
              "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=com.github.premnirmal.shared.CommonParcelize"
          )
        }
      }
    }
  }

  sourceSets {
    commonMain {
      kotlin.srcDir(generateChangelog)
    }
    commonMain.dependencies {
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
      implementation(libs.xmlutil.serialization)
      api(libs.room.runtime)
      implementation(libs.androidx.sqlite.bundled)
      implementation(libs.koin.core)
      api(libs.androidx.lifecycle.viewmodel)
      implementation(libs.navigation.compose)
      implementation(libs.androidx.datastore.preferences.core)
      implementation(libs.okio)
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(libs.coil.compose)
      implementation(libs.reorderable)
      api(libs.vico.multiplatform)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.timber)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.ktor.client.mock)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

// Generate the Compose Multiplatform resource accessor (`Res`) into a fixed, public package so the
// shared Compose UI (and the iOS host) can reference the bundled drawables deterministically.
compose.resources {
  publicResClass = true
  packageOfResClass = "com.github.premnirmal.shared.resources"
  generateResClass = always
}

android {
  namespace = "com.github.premnirmal.shared"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

detekt {
  toolVersion = libs.versions.detekt.get()
  config.setFrom(files("../config/detekt/detekt.yml", "../config/detekt/detekt-formatting.yml"))
  baseline = file("../config/detekt/baseline-shared.xml")
}

// Detekt's Gradle plugin does not auto-discover Kotlin Multiplatform source sets (its `sourceSets`
// expects JVM `SourceSet`s, which KMP doesn't expose), so the aggregate `:shared:detekt` task is
// NO-SOURCE by default. Point the Detekt task's `source` at the actual shared source directories.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  source = fileTree("src/commonMain/kotlin") + fileTree("src/androidMain/kotlin")
}

// CMP navigation-compose is pinned to the stable 2.9.2 line (see gradle/libs.versions.toml),
// which pulls androidx.navigation:*:2.9.x for Android. Guard against any transitive bump to the
// 2.10.x pre-release line (which requires compileSdk 37 + AGP 9.x) by forcing it back to the
// Jetpack 2.8.x line that the project already uses — the CMP wrapper klib stays ABI-compatible.
configurations.matching { it.name.contains("Android") || it.name.contains("android") || it.name.startsWith("debug") || it.name.startsWith("release") }
  .configureEach {
    resolutionStrategy.eachDependency {
      if (requested.group == "androidx.navigation" && requested.version?.startsWith("2.10") == true) {
        useVersion("2.8.5")
      }
    }
  }

// Room KSP processor for every Kotlin target that contains the shared persistence engine
// (the @Database/@Dao/@Entity declarations live in commonMain).
dependencies {
  add("kspAndroid", libs.room.compiler)
  detektPlugins(libs.detekt.formatting)
  detektPlugins(libs.detekt.compose)
}
