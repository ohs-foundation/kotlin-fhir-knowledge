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

import co.touchlab.kermit.Logger
import dev.ohs.fhir.knowledge.db.KnowledgeDatabase
import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideEntity
import dev.ohs.fhir.knowledge.db.entities.ResourceMetadataEntity
import dev.ohs.fhir.knowledge.files.NpmFileManager
import dev.ohs.fhir.knowledge.npm.HttpNpmPackageDownloader
import dev.ohs.fhir.knowledge.npm.NpmPackageDownloader
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Manages knowledge artifacts on the device. Knowledge artifacts can be installed individually as
 * JSON files or from FHIR NPM packages.
 *
 * It coordinates three components:
 * - a knowledge database, indexing the knowledge artifacts stored in the local file system,
 * - an NPM file manager, looking after the files holding the knowledge artifacts, and
 * - an NPM package downloader, fetching the knowledge artifacts from an NPM package server.
 *
 * Knowledge artifacts are scoped to the application. Two applications using the knowledge manager
 * do not share a set of knowledge artifacts. On desktop, this holds only for applications that pass
 * their application id to [create].
 *
 * [close] the knowledge manager when the application is done with it. That closes the database and
 * the HTTP client packages are downloaded with.
 *
 * See [Clinical Reasoning](https://hl7.org/fhir/R4/clinicalreasoning-module.html) for the formal
 * definition of a knowledge artifact. This implementation treats the FHIR R4 metadata resources as
 * the knowledge artifacts, which is a slightly different set from the resources implementing the
 * [MetadataResource interface](https://www.hl7.org/fhir/R5/metadataresource.html) in FHIR R5.
 */
class KnowledgeManager
internal constructor(
  private val knowledgeDatabase: KnowledgeDatabase,
  private val fileSystem: FileSystem,
  private val npmFileManager: NpmFileManager,
  private val npmPackageDownloader: NpmPackageDownloader,
) : AutoCloseable {
  private val knowledgeDao = knowledgeDatabase.knowledgeDao()
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Downloads and installs the [fhirNpmPackages] from the NPM package server, along with their
   * transitive dependencies. The packages are unpacked into a directory the knowledge manager owns,
   * and the resources they hold are indexed in the database for later retrieval.
   *
   * FHIR NPM packages already present in the database are skipped. A package whose download raises
   * an [IOException] is logged and not installed.
   */
  suspend fun install(vararg fhirNpmPackages: FhirNpmPackage) {
    fhirNpmPackages
      .filter { knowledgeDao.getImplementationGuide(it.name, it.version) == null }
      .forEach {
        try {
          if (!npmFileManager.containsPackage(it.name, it.version)) {
            npmPackageDownloader.downloadPackage(
              it,
              npmFileManager.getPackageDir(it.name, it.version),
            )
          }
        } catch (e: IOException) {
          Logger.w(e) { "Unable to download package ${it.name} ${it.version}" }
        }
        try {
          val localFhirNpmPackageMetadata =
            npmFileManager.getLocalFhirNpmPackageMetadata(it.name, it.version)
          import(
            it.copy(canonical = localFhirNpmPackageMetadata.canonical ?: it.canonical),
            localFhirNpmPackageMetadata.rootDirectory,
          )
          install(*localFhirNpmPackageMetadata.dependencies.toTypedArray())
        } catch (e: Exception) {
          Logger.w(e) { "Unable to install package ${it.name} ${it.version}" }
        }
      }
  }

  /**
   * Imports the content of the [fhirNpmPackage] from the given [rootDirectory], indexing the
   * metadata of the FHIR resources it holds for later retrieval.
   *
   * FHIR NPM packages already present in the database are skipped.
   *
   * The package is recorded in the database together with its resources, after the files have been
   * read. An import that raises records nothing, and so does one whose [rootDirectory] does not
   * exist, so either can be run again.
   *
   * @return what the import indexed, skipped and failed to read. A package already present in the
   *   database returns an empty result, and so does a [rootDirectory] that does not exist.
   */
  suspend fun import(fhirNpmPackage: FhirNpmPackage, rootDirectory: Path): ImportResult {
    if (knowledgeDao.getImplementationGuide(fhirNpmPackage.name, fhirNpmPackage.version) != null) {
      return ImportResult(indexed = 0, skipped = 0, failed = emptyList())
    }
    // TODO: copy the files into storage the knowledge manager owns?
    val files =
      withContext(ioDispatcher) { fileSystem.listOrNull(rootDirectory) }
        ?: return ImportResult(indexed = 0, skipped = 0, failed = emptyList())
    val resources = mutableListOf<ResourceMetadataEntity>()
    var skipped = 0
    val failed = mutableListOf<Path>()
    files.sorted().forEach { file ->
      val resource =
        try {
          readMetadataResourceOrNull(file)
        } catch (e: Exception) {
          Logger.w(e) { "Unable to load resource from $file" }
          failed += file
          return@forEach
        }
      if (resource == null) {
        Logger.d { "Skipped $file, which holds no knowledge artifact" }
        skipped++
        return@forEach
      }
      resources +=
        ResourceMetadataEntity(
          0,
          resource.resourceType,
          resource.url,
          resource.name,
          resource.version,
          file,
        )
    }
    knowledgeDao.insertImplementationGuide(
      ImplementationGuideEntity(
        0L,
        fhirNpmPackage.canonical ?: "",
        fhirNpmPackage.name,
        fhirNpmPackage.version,
        rootDirectory,
      ),
      resources,
    )
    return ImportResult(resources.size, skipped, failed)
  }

  /**
   * Indexes the knowledge artifact held as a JSON object in the given [file].
   *
   * This records the metadata of the knowledge artifact and the location of the file. When the
   * knowledge artifact is asked for, the knowledge manager reads the file, deserializes it and
   * returns the resulting FHIR resource.
   *
   * The operation neither copies the knowledge artifact nor checksums the content of the file, so
   * later retrievals are not guaranteed to produce the same result. Applications using this
   * function must weigh the risk of the file being modified or corrupted, which could make decision
   * support or measure evaluation return an incorrect or inaccurate result.
   *
   * Use this API for knowledge artifacts held in immutable files, such as the ones bundled with the
   * application.
   */
  suspend fun index(file: Path) {
    val resource = readMetadataResourceOrThrow(file)
    knowledgeDao.insertResource(
      null,
      ResourceMetadataEntity(
        0L,
        resource.resourceType,
        resource.url,
        resource.name,
        resource.version,
        file,
      ),
    )
  }

  /** Loads the resources of the implementation guides listed as dependencies. */
  @Deprecated("Load resources using URLs only")
  @Suppress("DEPRECATION")
  suspend fun loadResources(
    resourceType: String,
    url: String? = null,
    id: String? = null,
    name: String? = null,
    version: String? = null,
  ): Iterable<Resource> {
    val resType = ResourceType.fromCode(resourceType)

    val resourceEntities =
      when {
        url != null && version != null ->
          listOfNotNull(knowledgeDao.getResourceWithUrlAndVersion(resType, url, version))

        url != null -> listOfNotNull(knowledgeDao.getResourceWithUrl(resType, url))

        id != null -> listOfNotNull(knowledgeDao.getResourcesWithId(id.toLong()))

        name != null && version != null ->
          listOfNotNull(knowledgeDao.getResourcesWithNameAndVersion(resType, name, version))

        name != null -> knowledgeDao.getResourcesWithName(resType, name)

        else -> knowledgeDao.getResources(resType)
      }
    return resourceEntities.map { readMetadataResourceOrThrow(it.resourceFile).resource }
  }

  /**
   * Loads the knowledge artifact identified by its canonical URL and an optional version.
   *
   * The version can be passed either as a parameter or as part of the URL, separated from it by a
   * pipe `|`. Passing the URL `http://abc.xyz/fhir/Library|1.0.0` with no version is the same as
   * passing the URL `http://abc.xyz/fhir/Library` and the version `1.0.0`.
   *
   * A version given both ways has to be given the same both ways.
   *
   * @throws IllegalArgumentException if the URL holds more than one pipe `|`
   * @throws IllegalArgumentException if the version in the URL and the explicit version disagree
   */
  suspend fun loadResources(url: String, version: String? = null): Iterable<Resource> {
    val (canonicalUrl, canonicalVersion) = canonicalizeUrlAndVersion(url, version ?: "")

    val resourceEntities =
      if (canonicalVersion == "") {
        knowledgeDao.getResource(canonicalUrl)
      } else {
        listOfNotNull(knowledgeDao.getResource(canonicalUrl, canonicalVersion))
      }
    return resourceEntities.map { readMetadataResourceOrThrow(it.resourceFile).resource }
  }

  /**
   * Canonicalizes the URL and the version, splitting off a version carried in the URL after a pipe
   * `|`.
   *
   * The URL `http://abc.xyz/fhir/Library|1.0.0`, for instance, canonicalizes to the URL
   * `http://abc.xyz/fhir/Library` and the version `1.0.0`.
   *
   * @throws IllegalArgumentException if the URL holds more than one pipe
   * @throws IllegalArgumentException if the version in the URL and the explicit version disagree
   */
  private fun canonicalizeUrlAndVersion(url: String, version: String): Pair<String, String> {
    if (!url.contains('|')) {
      return Pair(url, version)
    }

    val parts = url.split('|')
    require(parts.size == 2) { "URL $url contains too many parts separated by \"|\"" }

    // If an explicit version is specified, it must match the one in the URL
    require(version == "" || version == parts[1]) {
      "Version specified in the URL ${parts[1]} and explicit version $version do not match"
    }
    return Pair(parts[0], parts[1])
  }

  /**
   * Deletes the given implementation guides.
   *
   * The files of an implementation guide are deleted only when they are in the package cache. A
   * directory passed to [import] is left as it is.
   */
  suspend fun delete(vararg igDependencies: FhirNpmPackage) {
    igDependencies.forEach { igDependency ->
      val igEntity = knowledgeDao.getImplementationGuide(igDependency.name, igDependency.version)
      if (igEntity != null) {
        knowledgeDao.deleteImplementationGuide(igDependency.name, igDependency.version)
        if (npmFileManager.containsPath(igEntity.rootDirectory)) {
          withContext(ioDispatcher) {
            try {
              fileSystem.deleteRecursively(igEntity.rootDirectory, mustExist = false)
            } catch (e: IOException) {
              // A directory that will not delete must not cost the remaining guides their turn.
              Logger.w(e) { "Unable to delete ${igEntity.rootDirectory}" }
            }
          }
        }
      }
    }
  }

  /** Closes the database and the NPM package downloader. */
  override fun close() {
    (npmPackageDownloader as? AutoCloseable)?.close()
    knowledgeDatabase.close()
  }

  /**
   * Reads the file as a FHIR metadata resource in JSON, or returns null if it is a directory or
   * holds anything else.
   */
  private suspend fun readMetadataResourceOrNull(file: Path): MetadataResource? =
    withContext(ioDispatcher) {
      if (fileSystem.metadataOrNull(file)?.isDirectory == true) {
        null
      } else {
        json.decodeMetadataResourceOrNull(fileSystem.read(file) { readUtf8() })
      }
    }

  /**
   * Reads the file as a FHIR metadata resource in JSON, or raises if it holds anything else.
   *
   * @throws IllegalStateException if the file holds no FHIR metadata resource
   */
  private suspend fun readMetadataResourceOrThrow(file: Path): MetadataResource =
    checkNotNull(readMetadataResourceOrNull(file)) { "Resource in $file is not a MetadataResource" }

  companion object {
    private const val DOWNLOADED_DATA_SUB_DIR = ".fhir_package_cache"
    private const val DEFAULT_PACKAGE_SERVER = "https://packages.fhir.org/packages/"

    /**
     * Creates a [KnowledgeManager] backed by the platform's own storage.
     *
     * ```
     * val knowledgeManager = KnowledgeManager.create(platformContext = context)
     * knowledgeManager.install(FhirNpmPackage("anc-cds", "0.3.0"))
     * ```
     *
     * @param platformContext the application `Context` on Android, where it locates both the
     *   database and the package cache. On desktop, the application id, which scopes both under
     *   `~/.fhir-knowledge/<application id>`; without one, every desktop application shares
     *   `~/.fhir-knowledge`. Ignored on iOS and the web, which have an OS-provided
     *   application-scoped directory.
     * @param inMemory whether the index lives only as long as the process, rather than in a
     *   database file. Downloaded packages are written to disk either way.
     * @param downloadedNpmDir the directory to keep downloaded NPM packages under. Defaults to an
     *   application-scoped directory the platform provides.
     * @param packageServer the package server to download FHIR NPM packages from, whose URL must
     *   end in a `/`.
     * @param fileSystem the file system holding the knowledge artifacts. Pass a `FakeFileSystem` to
     *   keep a test off disk.
     */
    fun create(
      platformContext: Any = Unit,
      inMemory: Boolean = false,
      downloadedNpmDir: Path? = null,
      packageServer: String = DEFAULT_PACKAGE_SERVER,
      fileSystem: FileSystem = systemFileSystem,
    ): KnowledgeManager {
      val npmDir =
        (downloadedNpmDir ?: defaultNpmDirectory(platformContext)) / DOWNLOADED_DATA_SUB_DIR
      return KnowledgeManager(
        knowledgeDatabaseBuilder(platformContext, inMemory).build(),
        fileSystem,
        NpmFileManager(fileSystem, npmDir),
        HttpNpmPackageDownloader(packageServer, fileSystem),
      )
    }
  }
}
