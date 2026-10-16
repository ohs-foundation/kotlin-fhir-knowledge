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
import dev.ohs.fhir.knowledge.npm.NpmPackageDownloader
import dev.ohs.fhir.model.r4.Library
import dev.ohs.fhir.model.r4.PlanDefinition
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class KnowledgeManagerTest {
  private val fileSystem = FakeFileSystem()
  private val knowledgeDb = inMemoryKnowledgeDatabase()
  private val fhirNpmPackage = FhirNpmPackage("anc-cds", "0.3.0", "http://url.com")
  private val dataFolder = "/anc-cds".toPath()
  private val knowledgeManager =
    knowledgeManager(fileSystem, knowledgeDb) { _, _ -> error("no download expected") }

  // Mirrors android-fhir's testdata/anc-cds folder: the same six knowledge artifacts, carrying the
  // ids, names, canonical URLs and versions the real files do, alongside the package manifest that
  // is not one. Only the CQL payload is left out; the knowledge manager never reads it, and the six
  // real files run to 6.7 MB.
  init {
    fileSystem.createDirectories(dataFolder)
    writeResource(dataFolder / "Library-WHOCommon.json", library("WHOCommon", WHO_COMMON_URL))
    writeResource(dataFolder / "Library-FHIRCommon.json", library("FHIRCommon", FHIR_COMMON_URL))
    writeResource(
      dataFolder / "Library-FHIRHelpers.json",
      library("FHIRHelpers", "$ANC_CDS/Library/FHIRHelpers", "4.0.1"),
    )
    writeResource(
      dataFolder / "Library-ANCStratifiers.json",
      library("ANCStratifiers", "$ANC_CDS/Library/ANCStratifiers"),
    )
    writeResource(
      dataFolder / "Library-ANCIND01.json",
      library("ANCIND01", "$ANC_CDS/Library/ANCIND01"),
    )
    writeResource(dataFolder / "Measure-ANCIND01.json", measure("ANCIND01", ANCIND01_URL))
    fileSystem.write(dataFolder / "package.json") {
      writeUtf8("""{"name":"anc-cds","version":"0.0.3","type":"IG"}""")
    }
  }

  @AfterTest
  fun closeDb() {
    knowledgeDb.close()
  }

  @Test
  fun shouldIndexTheResourcesOfAnImportedImplementationGuide() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    val implementationGuideId =
      knowledgeDb.knowledgeDao().getImplementationGuide("anc-cds", "0.3.0")!!.implementationGuideId
    val resources =
      knowledgeDb.knowledgeDao().getImplementationGuidesWithResources(implementationGuideId)
    assertEquals(6, resources?.resources?.size)
  }

  @Test
  fun shouldSkipAnImplementationGuideThatIsAlreadyImported() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    knowledgeManager.import(fhirNpmPackage, dataFolder)

    assertEquals(1, knowledgeDb.knowledgeDao().getImplementationGuides().size)
    assertEquals(6, knowledgeDb.knowledgeDao().getResources().size)
  }

  @Test
  fun shouldImportADirectoryThatDidNotExistAtTheFirstImport() = runTest {
    val lateFolder = "/anc-cds-late".toPath()

    val first = knowledgeManager.import(fhirNpmPackage, lateFolder)
    writeResource(lateFolder / "Library-WHOCommon.json", library("WHOCommon", WHO_COMMON_URL))
    val second = knowledgeManager.import(fhirNpmPackage, lateFolder)

    assertEquals(ImportResult(indexed = 0, skipped = 0, failed = emptyList()), first)
    assertEquals(ImportResult(indexed = 1, skipped = 0, failed = emptyList()), second)
  }

  @Test
  fun shouldImportAgainAfterAnImportThatRaised() = runTest {
    var listingFails = true
    val failingOnceFileSystem =
      object : ForwardingFileSystem(fileSystem) {
        override fun listOrNull(dir: Path): List<Path>? {
          if (listingFails) throw IOException("Unable to list $dir")
          return super.listOrNull(dir)
        }
      }
    val knowledgeManager =
      knowledgeManager(failingOnceFileSystem, knowledgeDb) { _, _ -> error("no download expected") }

    assertFailsWith<IOException> { knowledgeManager.import(fhirNpmPackage, dataFolder) }
    assertTrue(knowledgeDb.knowledgeDao().getImplementationGuides().isEmpty())
    listingFails = false
    val result = knowledgeManager.import(fhirNpmPackage, dataFolder)

    assertEquals(ImportResult(indexed = 6, skipped = 1, failed = emptyList()), result)
  }

  @Test
  fun shouldReportWhatAnImportIndexedSkippedAndFailed() = runTest {
    val broken = dataFolder / "Library-Broken.json"
    writeResource(broken, metadataResource("Library", "Broken", "$ANC_CDS/Library/Broken", VERSION))
    writeResource(
      dataFolder / "example" / "Patient-example.json",
      """{"resourceType":"Patient","id":"example"}""",
    )
    fileSystem.write(dataFolder / ".index.db") { writeUtf8("SQLite format 3") }

    val result = knowledgeManager.import(fhirNpmPackage, dataFolder)

    assertEquals(ImportResult(indexed = 6, skipped = 3, failed = listOf(broken)), result)
  }

  @Test
  fun shouldDeleteTheEntriesOfAnImportedImplementationGuideAndKeepItsFiles() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    knowledgeManager.delete(fhirNpmPackage)

    assertTrue(knowledgeDb.knowledgeDao().getImplementationGuides().isEmpty())
    assertTrue(knowledgeDb.knowledgeDao().getResources().isEmpty())
    assertTrue(fileSystem.exists(dataFolder / "Library-WHOCommon.json"))
  }

  @Test
  fun shouldReturnNothingForAnArtifactOfADeletedImplementationGuide() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    knowledgeManager.delete(fhirNpmPackage)

    assertEquals(0, knowledgeManager.loadResources(WHO_COMMON_URL, VERSION).count())
    assertEquals(0, knowledgeManager.loadResources(WHO_COMMON_URL).count())
  }

  @Test
  fun shouldKeepAnIndexedArtifactWhenAnImplementationGuideIsDeleted() = runTest {
    val file = "/Library-defaultA.json".toPath()
    writeResource(file, library("defaultA", LIBRARY_A_URL))
    knowledgeManager.index(file)
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    knowledgeManager.delete(fhirNpmPackage)

    assertEquals(1, knowledgeManager.loadResources(LIBRARY_A_URL).count())
  }

  @Test
  fun shouldLoadTheImportedResourcesByUrlAndVersion() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    assertEquals(1, knowledgeManager.loadResources(WHO_COMMON_URL, VERSION).count())
    assertEquals(1, knowledgeManager.loadResources(FHIR_COMMON_URL, VERSION).count())
    assertEquals(1, knowledgeManager.loadResources(ANCIND01_URL, VERSION).count())
  }

  @Test
  fun shouldReadTheVersionOutOfAPipeSeparatedUrl() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    assertEquals(1, knowledgeManager.loadResources("$WHO_COMMON_URL|$VERSION").count())
  }

  @Test
  fun shouldReturnNothingForAnArtifactThatWasNeverIndexed() = runTest {
    assertEquals(0, knowledgeManager.loadResources(WHO_COMMON_URL, VERSION).count())
    assertEquals(0, knowledgeManager.loadResources(WHO_COMMON_URL).count())
  }

  @Test
  fun shouldRejectAUrlWhoseVersionContradictsTheExplicitOne() = runTest {
    assertFailsWith<IllegalArgumentException> {
      knowledgeManager.loadResources("$WHO_COMMON_URL|$VERSION", "9.9.9")
    }
  }

  @Test
  fun shouldRejectAUrlWithMoreThanOnePipe() = runTest {
    assertFailsWith<IllegalArgumentException> {
      knowledgeManager.loadResources("$WHO_COMMON_URL|$VERSION|extra")
    }
  }

  @Test
  fun shouldCloseTheDatabase() = runTest {
    knowledgeManager.import(fhirNpmPackage, dataFolder)

    knowledgeManager.close()

    assertFails { knowledgeManager.loadResources(WHO_COMMON_URL) }
  }

  @Test
  fun shouldCloseADownloaderThatCanBeClosed() {
    val downloader =
      object : NpmPackageDownloader, AutoCloseable {
        var closed = false

        override suspend fun downloadPackage(fhirNpmPackage: FhirNpmPackage, packageFolder: Path) =
          error("no download expected")

        override fun close() {
          closed = true
        }
      }

    knowledgeManager(fileSystem, knowledgeDb, downloader).close()

    assertTrue(downloader.closed)
  }

  @Test
  fun shouldRaiseWhenIndexingSomethingOtherThanAMetadataResource() = runTest {
    val file = "/Patient-defaultA.json".toPath()
    writeResource(file, """{"resourceType":"Patient","id":"defaultA"}""")

    assertFailsWith<IllegalStateException> { knowledgeManager.index(file) }
  }

  @Test
  fun shouldIndexTwoVersionsOfTheSameLibrary() = runTest {
    val old = "/Library-defaultA-A.1.0.0.json".toPath()
    val new = "/Library-defaultA-A.1.0.1.json".toPath()
    writeResource(old, library("defaultA", "$LIBRARY_A_URL-A.1.0.0", "A.1.0.0"))
    writeResource(new, library("defaultA", "$LIBRARY_A_URL-A.1.0.1", "A.1.0.1"))

    knowledgeManager.index(old)
    knowledgeManager.index(new)

    assertEquals(2, knowledgeDb.knowledgeDao().getResources().size)
    val libraryA100 =
      knowledgeManager.loadResources("$LIBRARY_A_URL-A.1.0.0", "A.1.0.0").single() as Library
    assertEquals("A.1.0.0", libraryA100.version?.value)
    val libraryA101 =
      knowledgeManager.loadResources("$LIBRARY_A_URL-A.1.0.1", "A.1.0.1").single() as Library
    assertEquals("A.1.0.1", libraryA101.version?.value)
  }

  @Test
  fun shouldLoadResourcesOfDifferentTypesByUrl() = runTest {
    val libraryFile = "/Library-lId.json".toPath()
    val planDefinitionFile = "/PlanDefinition-pdId.json".toPath()
    writeResource(libraryFile, library("LibraryName", "$SAMPLE_URL/Library/lId", version = null))
    writeResource(
      planDefinitionFile,
      planDefinition("PlanDefinitionName", "$SAMPLE_URL/PlanDefinition/pdId", version = null),
    )

    knowledgeManager.index(libraryFile)
    knowledgeManager.index(planDefinitionFile)

    assertEquals(2, knowledgeDb.knowledgeDao().getResources().size)
    val library = knowledgeManager.loadResources("$SAMPLE_URL/Library/lId").single() as Library
    assertEquals("LibraryName", library.name?.value)
    val planDefinition =
      knowledgeManager.loadResources("$SAMPLE_URL/PlanDefinition/pdId").single() as PlanDefinition
    assertEquals("PlanDefinitionName", planDefinition.name?.value)
  }

  @Test
  fun shouldLoadResourcesOfDifferentTypesThatShareAVersionByUrl() = runTest {
    val libraryFile = "/Library-lId-versioned.json".toPath()
    val planDefinitionFile = "/PlanDefinition-pdId-versioned.json".toPath()
    writeResource(libraryFile, library("LibraryName", "$SAMPLE_URL/Library/lId", "0"))
    writeResource(
      planDefinitionFile,
      planDefinition("PlanDefinitionName", "$SAMPLE_URL/PlanDefinition/pdId", "0"),
    )

    knowledgeManager.index(libraryFile)
    knowledgeManager.index(planDefinitionFile)

    assertEquals(2, knowledgeDb.knowledgeDao().getResources().size)
    val library = knowledgeManager.loadResources("$SAMPLE_URL/Library/lId").single() as Library
    assertEquals("LibraryName", library.name?.value)
    val planDefinition =
      knowledgeManager.loadResources("$SAMPLE_URL/PlanDefinition/pdId").single() as PlanDefinition
    assertEquals("PlanDefinitionName", planDefinition.name?.value)
  }

  private fun writeResource(path: Path, json: String) {
    path.parent?.let(fileSystem::createDirectories)
    fileSystem.write(path) { writeUtf8(json) }
  }

  /** R4 requires `Library.type`, so a library needs one more element than the other artifacts. */
  private fun library(name: String, url: String, version: String? = VERSION) =
    metadataResource("Library", name, url, version).removeSuffix("}") +
      ""","type":{"coding":[{"code":"logic-library"}]}}"""

  private fun measure(name: String, url: String, version: String? = VERSION) =
    metadataResource("Measure", name, url, version)

  private fun planDefinition(name: String, url: String, version: String? = VERSION) =
    metadataResource("PlanDefinition", name, url, version)

  private fun metadataResource(type: String, name: String, url: String, version: String?) =
    """
    {"resourceType":"$type","id":"$name","name":"$name","url":"$url",
     ${version?.let { "\"version\":\"$it\"," } ?: ""}"status":"active"}
    """
      .trimIndent()

  private companion object {
    const val VERSION = "0.3.0"
    const val ANC_CDS = "http://fhir.org/guides/who/anc-cds"
    const val WHO_COMMON_URL = "$ANC_CDS/Library/WHOCommon"
    const val FHIR_COMMON_URL = "$ANC_CDS/Library/FHIRCommon"
    const val ANCIND01_URL = "$ANC_CDS/Measure/ANCIND01"
    const val LIBRARY_A_URL = "www.exampleA.com/Library/defaultA"
    const val SAMPLE_URL = "www.sample-url.com"
  }
}
