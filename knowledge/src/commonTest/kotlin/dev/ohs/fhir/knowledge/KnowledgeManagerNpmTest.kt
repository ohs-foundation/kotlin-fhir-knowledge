/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.knowledge

import dev.ohs.fhir.knowledge.db.inMemoryKnowledgeDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class KnowledgeManagerNpmTest {
  private val fileSystem = FakeFileSystem()
  private val knowledgeDb = inMemoryKnowledgeDatabase()
  private val downloadedPackages = mutableSetOf<FhirNpmPackage>()

  /** Stands in for a package server: a download writes the package manifest into the cache. */
  private val knowledgeManager =
    knowledgeManager(fileSystem, knowledgeDb) { fhirNpmPackage, packageFolder ->
      downloadedPackages += fhirNpmPackage
      cachePackage(fhirNpmPackage, packageFolder)
    }

  @AfterTest
  fun closeDb() {
    knowledgeDb.close()
  }

  @Test
  fun shouldDownloadThePackageAndItsTransitiveDependencies() = runTest {
    knowledgeManager.install(PACKAGE_1)

    assertEquals(setOf(PACKAGE_1, PACKAGE_2, PACKAGE_3), downloadedPackages)
  }

  @Test
  fun shouldDownloadNothingWhenEveryPackageIsCached() = runTest {
    listOf(PACKAGE_1, PACKAGE_2, PACKAGE_3).forEach { cachePackage(it, packageFolder(it)) }

    knowledgeManager.install(PACKAGE_1)

    assertTrue(downloadedPackages.isEmpty())
  }

  @Test
  fun shouldDownloadOnlyThePackagesThatAreNotCached() = runTest {
    listOf(PACKAGE_2, PACKAGE_3).forEach { cachePackage(it, packageFolder(it)) }

    knowledgeManager.install(PACKAGE_1, PACKAGE_2)

    assertEquals(setOf(PACKAGE_1), downloadedPackages)
  }

  @Test
  fun shouldSkipAPackageAlreadyRecordedInTheDatabase() = runTest {
    knowledgeManager.install(PACKAGE_1)
    downloadedPackages.clear()

    knowledgeManager.install(PACKAGE_1)

    assertTrue(downloadedPackages.isEmpty())
  }

  @Test
  fun shouldNotInstallAPackageWhoseDownloadRaisesAnIOException() = runTest {
    val knowledgeManager =
      knowledgeManager(fileSystem, knowledgeDb) { _, _ -> throw IOException("Unexpected code 404") }

    knowledgeManager.install(PACKAGE_3)

    assertTrue(knowledgeDb.knowledgeDao().getImplementationGuides().isEmpty())
  }

  @Test
  fun shouldRaiseWhatADownloadRaisesOtherThanAnIOException() = runTest {
    val knowledgeManager =
      knowledgeManager(fileSystem, knowledgeDb) { _, _ -> throw UnsupportedOperationException() }

    assertFailsWith<UnsupportedOperationException> { knowledgeManager.install(PACKAGE_3) }
  }

  @Test
  fun shouldRecordTheCanonicalUrlOfThePackageManifest() = runTest {
    val packageWithCanonical = PACKAGE_3.copy(canonical = CANONICAL)
    cachePackage(packageWithCanonical, packageFolder(packageWithCanonical))

    knowledgeManager.install(PACKAGE_3)

    assertEquals(CANONICAL, implementationGuideUrl(PACKAGE_3))
  }

  @Test
  fun shouldRecordTheCanonicalUrlOfTheCallerWhenThePackageManifestHasNone() = runTest {
    cachePackage(PACKAGE_3, packageFolder(PACKAGE_3))

    knowledgeManager.install(PACKAGE_3.copy(canonical = CANONICAL))

    assertEquals(CANONICAL, implementationGuideUrl(PACKAGE_3))
  }

  @Test
  fun shouldDeleteTheFilesOfAnInstalledPackage() = runTest {
    knowledgeManager.install(PACKAGE_3)

    knowledgeManager.delete(PACKAGE_3)

    assertTrue(knowledgeDb.knowledgeDao().getImplementationGuides().isEmpty())
    assertFalse(fileSystem.exists(packageFolder(PACKAGE_3) / "package"))
  }

  private suspend fun implementationGuideUrl(fhirNpmPackage: FhirNpmPackage) =
    knowledgeDb
      .knowledgeDao()
      .getImplementationGuide(fhirNpmPackage.name, fhirNpmPackage.version)
      ?.url

  private fun packageFolder(fhirNpmPackage: FhirNpmPackage) =
    NPM_ROOT / "${fhirNpmPackage.name}#${fhirNpmPackage.version}"

  private fun cachePackage(fhirNpmPackage: FhirNpmPackage, packageFolder: Path) {
    val manifestDir = packageFolder / "package"
    fileSystem.createDirectories(manifestDir)
    fileSystem.write(manifestDir / "package.json") { writeUtf8(manifest(fhirNpmPackage)) }
  }

  private fun manifest(fhirNpmPackage: FhirNpmPackage): String {
    val dependency = DEPENDENCIES[fhirNpmPackage]
    val dependencies =
      dependency?.let { ""","dependencies":{"${it.name}":"${it.version}"}""" } ?: ""
    val canonical = fhirNpmPackage.canonical?.let { ""","canonical":"$it"""" } ?: ""
    return """{"name":"${fhirNpmPackage.name}","version":"${fhirNpmPackage.version}"$canonical$dependencies}"""
  }

  private companion object {
    val NPM_ROOT = "/npm".toPath()
    val PACKAGE_1 = FhirNpmPackage("package1", "version")
    val PACKAGE_2 = FhirNpmPackage("package2", "version")
    val PACKAGE_3 = FhirNpmPackage("package3", "version")
    const val CANONICAL = "http://example.org/fhir/package3"
    val DEPENDENCIES = mapOf(PACKAGE_1 to PACKAGE_2, PACKAGE_2 to PACKAGE_3)
  }
}
