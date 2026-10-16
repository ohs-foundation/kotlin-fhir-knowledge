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
package dev.ohs.fhir.knowledge.npm

import dev.ohs.fhir.knowledge.FhirNpmPackage
import dev.ohs.fhir.knowledge.NPM_PACKAGE_MANIFEST
import dev.ohs.fhir.knowledge.NPM_PACKAGE_TGZ
import dev.ohs.fhir.knowledge.PATH_TRAVERSAL_TGZ
import dev.ohs.fhir.knowledge.files.NpmFileManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class HttpNpmPackageDownloaderTest {
  private val fileSystem = FakeFileSystem()
  private val npmFileManager = NpmFileManager(fileSystem, CACHE_ROOT)

  @Test
  fun shouldUnpackTheDownloadedPackageByteForByte() = runTest {
    val requested = mutableListOf<String>()
    val downloader =
      downloader(
        MockEngine {
          requested += it.url.toString()
          respond(NPM_PACKAGE_TGZ)
        }
      )
    val fhirNpmPackage = FhirNpmPackage(PACKAGE_ID, VERSION)
    val packageDir = npmFileManager.getPackageDir(PACKAGE_ID, VERSION)

    downloader.downloadPackage(fhirNpmPackage, packageDir)

    assertEquals(listOf("$PACKAGE_SERVER$PACKAGE_ID/$VERSION"), requested)
    val manifest = packageDir / "package" / "package.json"
    assertEquals(NPM_PACKAGE_MANIFEST.encodeUtf8(), fileSystem.read(manifest) { readByteString() })
  }

  @Test
  fun shouldReadTheUnpackedPackageBackAsMetadata() = runTest {
    val downloader = downloader(MockEngine { respond(NPM_PACKAGE_TGZ) })
    val fhirNpmPackage = FhirNpmPackage(PACKAGE_ID, VERSION)

    downloader.downloadPackage(fhirNpmPackage, npmFileManager.getPackageDir(PACKAGE_ID, VERSION))

    val npmPackage = npmFileManager.getLocalFhirNpmPackageMetadata(PACKAGE_ID, VERSION)
    assertEquals(PACKAGE_ID, npmPackage.packageId)
    assertEquals(VERSION, npmPackage.version)
    assertEquals(DEPENDENCIES, npmPackage.dependencies)
  }

  @Test
  fun shouldRemoveTheArchiveOnceUnpacked() = runTest {
    val downloader = downloader(MockEngine { respond(NPM_PACKAGE_TGZ) })
    val packageDir = npmFileManager.getPackageDir(PACKAGE_ID, VERSION)

    downloader.downloadPackage(FhirNpmPackage(PACKAGE_ID, VERSION), packageDir)

    assertEquals(listOf(packageDir / "package"), fileSystem.list(packageDir))
  }

  @Test
  fun shouldRaiseWhenTheServerFails() = runTest {
    val downloader = downloader(MockEngine { respondError(HttpStatusCode.InternalServerError) })

    assertFailsWith<IOException> {
      downloader.downloadPackage(
        FhirNpmPackage(PACKAGE_ID, VERSION),
        npmFileManager.getPackageDir(PACKAGE_ID, VERSION),
      )
    }
  }

  @Test
  fun shouldRaiseAnIOExceptionWhenTheRequestFails() = runTest {
    val downloader = downloader(MockEngine { throw kotlinx.io.IOException("Connection refused") })

    assertFailsWith<IOException> {
      downloader.downloadPackage(
        FhirNpmPackage(PACKAGE_ID, VERSION),
        npmFileManager.getPackageDir(PACKAGE_ID, VERSION),
      )
    }
  }

  @Test
  fun shouldRejectAnEntryThatEscapesThePackageDirectory() = runTest {
    val downloader = downloader(MockEngine { respond(PATH_TRAVERSAL_TGZ) })
    val packageDir = npmFileManager.getPackageDir(PACKAGE_ID, VERSION)

    assertFailsWith<IOException> {
      downloader.downloadPackage(FhirNpmPackage(PACKAGE_ID, VERSION), packageDir)
    }

    assertTrue(fileSystem.list(CACHE_ROOT).none { it.name == "escaped.json" })
  }

  @Test
  fun shouldCloseTheHttpClient() {
    val client = HttpClient(MockEngine { respond(NPM_PACKAGE_TGZ) })

    HttpNpmPackageDownloader(PACKAGE_SERVER, fileSystem, client).close()

    assertFalse(client.isActive)
  }

  private fun downloader(engine: MockEngine) =
    HttpNpmPackageDownloader(PACKAGE_SERVER, fileSystem, HttpClient(engine))

  private companion object {
    const val PACKAGE_SERVER = "https://packages.example.org/packages/"
    const val PACKAGE_ID = "test-package"
    const val VERSION = "13.3.7"
    val CACHE_ROOT = "/cache".toPath()
    val DEPENDENCIES =
      listOf(
        FhirNpmPackage("hl7.fhir.r4.core", "4.0.1"),
        FhirNpmPackage("hl7.terminology.r4", "5.0.0"),
        FhirNpmPackage("hl7.fhir.fr.core", "1.1.0"),
      )
  }
}
