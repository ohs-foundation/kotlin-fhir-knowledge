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
package dev.ohs.fhir.knowledge.files

import dev.ohs.fhir.knowledge.FhirNpmPackage
import dev.ohs.fhir.knowledge.NPM_PACKAGE_MANIFEST
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class NpmFileManagerTest {
  private val fileSystem = FakeFileSystem()
  private val cacheRoot = "/cache".toPath()
  private val npmFileManager = NpmFileManager(fileSystem, cacheRoot)

  @Test
  fun shouldNameThePackageDirectoryAfterTheIdAndVersion() {
    val packageDir = npmFileManager.getPackageDir(PACKAGE_ID, VERSION)

    assertEquals(cacheRoot / "$PACKAGE_ID#$VERSION", packageDir)
  }

  @Test
  fun shouldReadTheCachedPackageMetadata() = runTest {
    writeManifest(VERSION)

    val npmPackage = npmFileManager.getLocalFhirNpmPackageMetadata(PACKAGE_ID, VERSION)

    assertEquals(PACKAGE_ID, npmPackage.packageId)
    assertEquals(VERSION, npmPackage.version)
    assertEquals("https://google.com/test-package", npmPackage.canonical)
    assertEquals(DEPENDENCIES, npmPackage.dependencies)
    assertEquals(cacheRoot / "$PACKAGE_ID#$VERSION" / "package", npmPackage.rootDirectory)
  }

  @Test
  fun shouldLeaveCanonicalNullWhenTheManifestOmitsIt() = runTest {
    val packageDir = cacheRoot / "$PACKAGE_ID#$VERSION" / "package"
    fileSystem.createDirectories(packageDir)
    fileSystem.write(packageDir / "package.json") {
      writeUtf8("""{"name":"$PACKAGE_ID","version":"$VERSION"}""")
    }

    val npmPackage = npmFileManager.getLocalFhirNpmPackageMetadata(PACKAGE_ID, VERSION)

    assertNull(npmPackage.canonical)
    assertEquals(emptyList(), npmPackage.dependencies)
  }

  @Test
  fun shouldReportACachedPackageAsPresent() = runTest {
    writeManifest(VERSION)

    assertTrue(npmFileManager.containsPackage(PACKAGE_ID, VERSION))
  }

  @Test
  fun shouldReportAnUncachedPackageAsAbsent() = runTest {
    writeManifest(VERSION)

    assertFalse(npmFileManager.containsPackage(PACKAGE_ID, MISSING_VERSION))
  }

  @Test
  fun shouldReportAPathInsideTheCacheAsContained() {
    assertTrue(npmFileManager.containsPath(cacheRoot / "$PACKAGE_ID#$VERSION" / "package"))
  }

  @Test
  fun shouldReportAPathOutsideTheCacheAsNotContained() {
    assertFalse(npmFileManager.containsPath("/assets/$PACKAGE_ID".toPath()))
    assertFalse(npmFileManager.containsPath(cacheRoot / ".." / "assets"))
    assertFalse(npmFileManager.containsPath("/cache-of-the-application".toPath()))
    assertFalse(npmFileManager.containsPath(cacheRoot))
  }

  private fun writeManifest(version: String) {
    val packageDir = cacheRoot / "$PACKAGE_ID#$version" / "package"
    fileSystem.createDirectories(packageDir)
    fileSystem.write(packageDir / "package.json") { writeUtf8(NPM_PACKAGE_MANIFEST) }
  }

  private companion object {
    const val PACKAGE_ID = "test-package"
    const val VERSION = "13.3.7"
    const val MISSING_VERSION = "13.3.8"
    val DEPENDENCIES =
      listOf(
        FhirNpmPackage("hl7.fhir.r4.core", "4.0.1"),
        FhirNpmPackage("hl7.terminology.r4", "5.0.0"),
        FhirNpmPackage("hl7.fhir.fr.core", "1.1.0"),
      )
  }
}
