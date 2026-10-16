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
import dev.ohs.fhir.knowledge.gunzip
import dev.ohs.fhir.knowledge.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use

/**
 * Fetches a FHIR NPM package over HTTP and unpacks it into the local cache.
 *
 * The archive is streamed to a temporary `.tgz` beside the target directory and expanded from
 * there, so that memory holds one entry at a time rather than the whole package.
 *
 * [client] defaults to the Ktor engine on the platform's classpath — OkHttp on Android, the Java
 * engine on the JVM, Darwin on iOS. Pass one to share a configured client, or a `MockEngine` in a
 * test.
 */
internal class HttpNpmPackageDownloader(
  private val packageServerUrl: String,
  private val fileSystem: FileSystem,
  private val client: HttpClient = HttpClient(),
) : NpmPackageDownloader, AutoCloseable {

  @Throws(IOException::class, CancellationException::class)
  override suspend fun downloadPackage(fhirNpmPackage: FhirNpmPackage, packageFolder: Path) {
    val packageName = fhirNpmPackage.name
    val version = fhirNpmPackage.version
    val response = raisingOkioIOException { client.get("$packageServerUrl$packageName/$version") }

    if (!response.status.isSuccess()) {
      throw IOException("Unexpected code ${response.status}")
    }

    val channel = response.bodyAsChannel()
    withContext(ioDispatcher) {
      fileSystem.createDirectories(packageFolder)
      val tgzFile = packageFolder / "$packageName-$version.tgz"
      try {
        saveResponseToFile(channel, tgzFile)
        extractTgzFile(tgzFile, packageFolder)
      } finally {
        fileSystem.delete(tgzFile, mustExist = false)
      }
    }
  }

  /** Closes the [client]. */
  override fun close() {
    client.close()
  }

  private suspend fun saveResponseToFile(channel: ByteReadChannel, file: Path) {
    val buffer = ByteArray(BUFFER_SIZE)
    fileSystem.sink(file).buffer().use { sink ->
      while (true) {
        val bytesRead = raisingOkioIOException { channel.readAvailable(buffer, 0, buffer.size) }
        if (bytesRead == -1) break
        sink.write(buffer, 0, bytesRead)
      }
    }
  }

  /**
   * Runs a Ktor call, raising the IOException of okio where Ktor raises the one of kotlinx-io. The
   * two are the same class on the JVM only.
   */
  private inline fun <T> raisingOkioIOException(block: () -> T): T =
    try {
      block()
    } catch (e: kotlinx.io.IOException) {
      throw IOException(e.message, e)
    }

  private suspend fun extractTgzFile(tgzFile: Path, outputFolder: Path) {
    fileSystem.source(tgzFile).use { source ->
      gunzip(source).buffer().use { archive ->
        archive.forEachTarEntry { entry, content ->
          val outputFile = outputFolder.resolveEntry(entry.name)
          if (entry.isDirectory) {
            fileSystem.createDirectories(outputFile)
          } else {
            outputFile.parent?.let(fileSystem::createDirectories)
            fileSystem.write(outputFile) { write(content) }
          }
        }
      }
    }
  }

  /**
   * Resolves an archive entry name against this directory, rejecting any that would escape it. A
   * package server is not a trusted source, and a tar entry name may hold `..` segments.
   */
  private fun Path.resolveEntry(name: String): Path {
    val resolved = (this / name).normalized()
    if (resolved.segments.take(segments.size) != segments) {
      throw IOException("Package entry $name escapes the package directory")
    }
    return resolved
  }

  private companion object {
    const val BUFFER_SIZE = 8 * 1024
  }
}
