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

import dev.ohs.fhir.knowledge.BAD_CHECKSUM_TGZ
import dev.ohs.fhir.knowledge.BAD_SIZE_TGZ
import dev.ohs.fhir.knowledge.BLOCK_BOUNDARY_TGZ
import dev.ohs.fhir.knowledge.GNU_LONGNAME_TGZ
import dev.ohs.fhir.knowledge.LEGACY_DIRECTORY_TGZ
import dev.ohs.fhir.knowledge.LONG_ENTRY_CONTENT
import dev.ohs.fhir.knowledge.LONG_ENTRY_NAME
import dev.ohs.fhir.knowledge.NO_TRAILER_TGZ
import dev.ohs.fhir.knowledge.NPM_PACKAGE_MANIFEST
import dev.ohs.fhir.knowledge.NPM_PACKAGE_TGZ
import dev.ohs.fhir.knowledge.PAX_LONG_ENTRY_CONTENT
import dev.ohs.fhir.knowledge.PAX_LONG_ENTRY_NAME
import dev.ohs.fhir.knowledge.PAX_LONG_NAME_TGZ
import dev.ohs.fhir.knowledge.TRUNCATED_TGZ
import dev.ohs.fhir.knowledge.USTAR_PREFIX_TGZ
import dev.ohs.fhir.knowledge.gunzip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import okio.buffer
import okio.use

class TarReaderTest {

  @Test
  fun shouldReadTheEntriesOfAnNpmPackage() = runTest {
    val entries = read(NPM_PACKAGE_TGZ)

    assertEquals(
      listOf(TarEntry("package/", 0, isDirectory = true), TarEntry("package/package.json", 306)),
      entries.map { it.first },
    )
  }

  /** Guards the whole gunzip-then-untar path: an off-by-one anywhere shows up in these bytes. */
  @Test
  fun shouldReadEntryContentByteForByte() = runTest {
    val (entry, content) = read(NPM_PACKAGE_TGZ).single { !it.first.isDirectory }

    assertEquals(NPM_PACKAGE_MANIFEST.encodeUtf8(), content)
    assertEquals(entry.size, content.size.toLong())
  }

  @Test
  fun shouldReadNameFromTheUstarPrefixField() = runTest {
    assertEquals(LONG_ENTRY_NAME to LONG_ENTRY_CONTENT, onlyFile(USTAR_PREFIX_TGZ))
  }

  @Test
  fun shouldReadNameFromAGnuLongNameRecord() = runTest {
    assertEquals(LONG_ENTRY_NAME to LONG_ENTRY_CONTENT, onlyFile(GNU_LONGNAME_TGZ))
  }

  /** The layout `npm pack` writes, where the header name is only a truncation of the real path. */
  @Test
  fun shouldReadNameFromAPaxExtendedHeader() = runTest {
    val entries = read(PAX_LONG_NAME_TGZ)

    assertEquals(listOf("package/", PAX_LONG_ENTRY_NAME), entries.map { it.first.name })
    assertEquals(PAX_LONG_ENTRY_NAME to PAX_LONG_ENTRY_CONTENT, onlyFile(PAX_LONG_NAME_TGZ))
  }

  /**
   * Content is padded to a 512-byte block, so a size of exactly one block, or one byte past it, is
   * where padding arithmetic goes wrong. Each entry is followed by another, which desynchronises
   * rather than silently truncating if it does.
   */
  @Test
  fun shouldStayAlignedAcrossBlockBoundarySizes() = runTest {
    val entries = read(BLOCK_BOUNDARY_TGZ)

    assertEquals(
      listOf(
        TarEntry("package/empty.json", 0),
        TarEntry("package/exact.json", 512),
        TarEntry("package/spill.json", 513),
        TarEntry("package/tail.json", 13),
      ),
      entries.map { it.first },
    )
    assertEquals("""{"last":true}""", entries.last().second.utf8())
  }

  /**
   * Written before the ustar type flag existed; unpacking it as a file breaks everything under it.
   */
  @Test
  fun shouldTreatATrailingSlashNameAsADirectory() = runTest {
    val entries = read(LEGACY_DIRECTORY_TGZ)

    assertEquals(
      listOf(TarEntry("package/", 0, isDirectory = true), TarEntry("package/nested.json", 7)),
      entries.map { it.first },
    )
  }

  @Test
  fun shouldReadAnArchiveWithNoTrailingZeroBlocks() = runTest {
    assertEquals("package/only.json" to """{"only":true}""", onlyFile(NO_TRAILER_TGZ))
  }

  @Test
  fun shouldRejectAHeaderWhoseChecksumDoesNotMatch() = runTest {
    assertFailsWith<IOException> { read(BAD_CHECKSUM_TGZ) }
  }

  @Test
  fun shouldRejectANonOctalNumericField() = runTest {
    assertFailsWith<IOException> { read(BAD_SIZE_TGZ) }
  }

  /** A half-written archive has to fail loudly rather than yield the entries it managed to read. */
  @Test
  fun shouldRejectATruncatedArchive() = runTest {
    assertFailsWith<IOException> { read(TRUNCATED_TGZ) }
  }

  private suspend fun read(tgz: ByteArray): List<Pair<TarEntry, ByteString>> = buildList {
    gunzip(Buffer().write(tgz)).buffer().use { archive ->
      archive.forEachTarEntry { entry, content -> add(entry to content) }
    }
  }

  /** The name and content of the archive's only file entry, for the single-file fixtures. */
  private suspend fun onlyFile(tgz: ByteArray) =
    read(tgz)
      .single { !it.first.isDirectory }
      .let { (entry, content) -> entry.name to content.utf8() }
}
