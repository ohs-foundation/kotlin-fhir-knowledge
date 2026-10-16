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

import androidx.room3.Room
import androidx.room3.RoomDatabase
import dev.ohs.fhir.knowledge.db.KnowledgeDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source

/**
 * Returns a builder backed by a SQLite-WASM Web Worker that persists to the Origin Private File
 * System. The query coroutine context is left to Room's default, since database access in the
 * browser is asynchronous throughout.
 *
 * @param platformContext ignored; the browser has no platform context.
 * @param inMemory whether the database lives only for as long as the page does.
 */
internal actual fun knowledgeDatabaseBuilder(
  platformContext: Any,
  inMemory: Boolean,
): RoomDatabase.Builder<KnowledgeDatabase> =
  (if (inMemory) {
      Room.inMemoryDatabaseBuilder<KnowledgeDatabase>()
    } else {
      Room.databaseBuilder<KnowledgeDatabase>(DATABASE_NAME)
    })
    .setDriver(sqliteWasmDriver())

/**
 * The browser has no directory tree, so this names a root within whichever [FileSystem] the caller
 * supplies to [KnowledgeManager.create].
 */
internal actual fun defaultNpmDirectory(platformContext: Any): Path = "/".toPath()

/**
 * Gzip is not implemented for the web targets, so [KnowledgeManager.install] cannot unpack a
 * downloaded package there. The remaining entry points do not decompress anything and are
 * unaffected: index artifacts that are bundled with the application or already unpacked, and
 * resolve them by canonical URL.
 *
 * okio implements gzip for the JVM and the native targets only. The browser's own
 * `DecompressionStream` could back an implementation here, which is why [gunzip] is declared
 * `suspend`.
 */
internal actual suspend fun gunzip(source: Source): Source =
  throw UnsupportedOperationException(
    "Gzip is not supported on the web targets, so FHIR NPM packages cannot be downloaded and " +
      "unpacked there. Index artifacts bundled with the application instead."
  )

/** The browser has no thread pool to move blocking work to; nothing here blocks. */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

/**
 * The browser exposes no file system that okio implements, so callers pass their own [FileSystem]
 * to [KnowledgeManager.create]. Reading this default is what fails when they do not.
 */
internal actual val systemFileSystem: FileSystem
  get() =
    throw UnsupportedOperationException(
      "The web targets have no system file system. Pass a FileSystem to KnowledgeManager.create()."
    )

private const val DATABASE_NAME = "knowledge.db"
