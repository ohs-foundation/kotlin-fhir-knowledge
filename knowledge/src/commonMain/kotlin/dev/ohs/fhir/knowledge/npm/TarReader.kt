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

import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

/**
 * A file or directory recorded in a tar archive.
 *
 * @property name the path of the entry relative to the archive root, with a trailing `/` on a
 *   directory.
 * @property size the length in bytes of the entry's content, always zero for a directory.
 */
internal data class TarEntry(val name: String, val size: Long, val isDirectory: Boolean = false)

/**
 * Reads a tar archive, calling [action] with each entry and its content.
 *
 * Entries are held in memory one at a time; the files in a FHIR NPM package are individual FHIR
 * resources, so none of them is large. Directory entries are reported with empty content.
 *
 * A path too long for the historic 100-character name field reaches the reader in one of three
 * shapes, and all three are honoured: the ustar prefix field, GNU's `L` long-name record, and the
 * pax `path` record that node-tar — and so `npm pack` — writes. A pax record wins over the header
 * fields, which is the point of it: the header holds only a truncated approximation of the path.
 *
 * Any other type flag is skipped, covering the sparse, link and global-header records a reader is
 * not required to understand.
 *
 * @throws IOException if a header fails its own checksum, carries a malformed numeric field, or the
 *   archive ends part-way through an entry.
 */
internal fun BufferedSource.forEachTarEntry(action: (TarEntry, ByteString) -> Unit) {
  var longName: String? = null
  var paxName: String? = null
  while (!exhausted()) {
    val header = readByteArray(BLOCK_SIZE.toLong())
    // The archive ends with two zero-filled blocks, but a writer that simply stops is common
    // enough that neither the trailer nor the second block can be insisted on.
    if (header.all { it == ZERO }) return

    header.verifyChecksum()
    val size = header.readOctal(SIZE_OFFSET, SIZE_LENGTH)
    val typeFlag = header[TYPE_FLAG_OFFSET].toInt().toChar()
    val content = readByteString(size)

    when (typeFlag) {
      LONG_NAME_TYPE -> longName = content.utf8().trimEnd(NUL)

      PAX_HEADER_TYPE -> paxName = content.paxPath()

      PAX_GLOBAL_HEADER_TYPE -> Unit

      FILE_TYPE,
      OLD_FILE_TYPE,
      DIRECTORY_TYPE -> {
        val name = paxName ?: longName ?: header.readName()
        paxName = null
        longName = null
        // Tars written before the ustar type flag existed mark a directory by the trailing slash
        // alone, and unpacking one as a regular file breaks every entry beneath it.
        val isDirectory = typeFlag == DIRECTORY_TYPE || name.endsWith("/")
        action(TarEntry(name, size, isDirectory), content)
      }

      else -> {
        paxName = null
        longName = null
      }
    }

    skipPadding(size)
  }
}

/**
 * Reads the `path` record out of a pax extended header. Records are laid out as `"<length>
 * <key>=<value>\n"`, where the length counts bytes rather than characters, so the scan has to stay
 * on the bytes to survive a non-ASCII path.
 */
private fun ByteString.paxPath(): String? {
  var offset = 0
  while (offset < size) {
    val space = indexOf(SPACE, offset)
    if (space <= offset) return null
    val recordLength = substring(offset, space).utf8().toIntOrNull() ?: return null
    if (recordLength <= 0 || offset + recordLength > size) return null

    val record = substring(space + 1, offset + recordLength)
    val separator = record.indexOf(EQUALS)
    if (separator > 0 && record.substring(0, separator) == PAX_PATH_KEY) {
      return record.substring(separator + 1).utf8().removeSuffix("\n")
    }
    offset += recordLength
  }
  return null
}

/**
 * Skips the NULs an entry's content is padded with, up to the next 512-byte block boundary. An
 * archive that stops right after its final entry simply has none left to skip.
 */
private fun BufferedSource.skipPadding(size: Long) {
  val padding = (BLOCK_SIZE - size % BLOCK_SIZE) % BLOCK_SIZE
  if (padding > 0 && request(padding)) skip(padding)
}

/**
 * Reads the entry name, which ustar splits across a 155-character prefix and the original
 * 100-character name when the whole path does not fit in the latter.
 */
private fun ByteArray.readName(): String {
  val name = readString(NAME_OFFSET, NAME_LENGTH)
  val prefix = readString(PREFIX_OFFSET, PREFIX_LENGTH)
  return if (prefix.isEmpty()) name else "$prefix/$name"
}

private fun ByteArray.readString(offset: Int, length: Int): String {
  val end = (offset until offset + length).firstOrNull { this[it] == ZERO } ?: (offset + length)
  return decodeToString(offset, end)
}

/**
 * Checks the header against its own checksum, the sum of every header byte with the checksum field
 * itself counted as spaces. Writers have historically summed the bytes as signed or as unsigned, so
 * either total is accepted.
 *
 * A package server is not a trusted source, and the size this guards is the number of bytes the
 * reader then allocates for the entry.
 */
private fun ByteArray.verifyChecksum() {
  val expected = readOctal(CHECKSUM_OFFSET, CHECKSUM_LENGTH)
  var unsigned = 0L
  var signed = 0L
  for (index in indices) {
    val byte = if (index in CHECKSUM_FIELD) SPACE_BYTE else this[index]
    unsigned += byte.toLong() and 0xFF
    signed += byte.toLong()
  }
  if (expected != unsigned && expected != signed) {
    throw IOException("Malformed tar header: checksum $expected does not match the header")
  }
}

/** Reads a tar numeric field, which is an octal string padded with NULs or spaces. */
private fun ByteArray.readOctal(offset: Int, length: Int): Long {
  val digits = readString(offset, length).trim()
  if (digits.isEmpty()) return 0L
  return digits.toLongOrNull(radix = 8) ?: throw IOException("Malformed tar numeric field: $digits")
}

private const val BLOCK_SIZE = 512
private const val NAME_OFFSET = 0
private const val NAME_LENGTH = 100
private const val SIZE_OFFSET = 124
private const val SIZE_LENGTH = 12
private const val CHECKSUM_OFFSET = 148
private const val CHECKSUM_LENGTH = 8
private const val TYPE_FLAG_OFFSET = 156
private const val PREFIX_OFFSET = 345
private const val PREFIX_LENGTH = 155

private const val ZERO: Byte = 0
private const val SPACE_BYTE: Byte = 32
private const val NUL = '\u0000'
private const val OLD_FILE_TYPE = '\u0000'
private const val FILE_TYPE = '0'
private const val DIRECTORY_TYPE = '5'
private const val LONG_NAME_TYPE = 'L'
private const val PAX_HEADER_TYPE = 'x'
private const val PAX_GLOBAL_HEADER_TYPE = 'g'

private val CHECKSUM_FIELD = CHECKSUM_OFFSET until CHECKSUM_OFFSET + CHECKSUM_LENGTH

private val SPACE = " ".encodeUtf8()
private val EQUALS = "=".encodeUtf8()
private val PAX_PATH_KEY = "path".encodeUtf8()
