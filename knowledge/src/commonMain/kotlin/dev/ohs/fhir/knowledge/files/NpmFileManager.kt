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
import dev.ohs.fhir.knowledge.LocalFhirNpmPackageMetadata
import dev.ohs.fhir.knowledge.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path

/** Manages stored NPM packages. */
internal class NpmFileManager(private val fileSystem: FileSystem, private val cacheRoot: Path) {
  /** Returns the package directory for the given [packageId] and [version]. */
  fun getPackageDir(packageId: String, version: String) = cacheRoot / "$packageId#$version"

  /** Returns whether the given [path] is inside the package cache. */
  fun containsPath(path: Path): Boolean {
    val cache = cacheRoot.normalized()
    val normalized = path.normalized()
    return normalized.root == cache.root &&
      normalized.segments.size > cache.segments.size &&
      normalized.segments.take(cache.segments.size) == cache.segments
  }

  /** Returns whether the package for the given [packageId] and [version] is cached. */
  suspend fun containsPackage(packageId: String, version: String): Boolean =
    withContext(ioDispatcher) { fileSystem.exists(manifestPath(packageId, version)) }

  /**
   * Returns the metadata of the cached package for the given [packageId] and [version].
   *
   * @throws okio.IOException if the package is not cached.
   */
  suspend fun getLocalFhirNpmPackageMetadata(
    packageId: String,
    version: String,
  ): LocalFhirNpmPackageMetadata =
    withContext(ioDispatcher) {
      val manifest = manifestPath(packageId, version)
      readNpmPackage(manifest, fileSystem.read(manifest) { readUtf8() })
    }

  private fun manifestPath(packageId: String, version: String) =
    getPackageDir(packageId, version) / PACKAGE_DIR / MANIFEST_NAME

  /** Creates a [LocalFhirNpmPackageMetadata] by parsing the package manifest file. */
  private fun readNpmPackage(manifest: Path, json: String): LocalFhirNpmPackageMetadata {
    val manifestJson = Json.parseToJsonElement(json).jsonObject
    val dependencies =
      manifestJson["dependencies"]?.jsonObject?.map { (name, version) ->
        FhirNpmPackage(name, version.jsonPrimitive.content)
      }

    return LocalFhirNpmPackageMetadata(
      packageId = manifestJson.string("name")!!,
      version = manifestJson.string("version")!!,
      canonical = manifestJson.string("canonical"),
      dependencies = dependencies ?: emptyList(),
      rootDirectory = manifest.parent!!,
    )
  }

  private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull

  private companion object {
    const val PACKAGE_DIR = "package"
    const val MANIFEST_NAME = "package.json"
  }
}
