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
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ohs.fhir.knowledge.db.KnowledgeDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import okio.GzipSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun knowledgeDatabaseBuilder(
  platformContext: Any,
  inMemory: Boolean,
): RoomDatabase.Builder<KnowledgeDatabase> =
  (if (inMemory) {
      Room.inMemoryDatabaseBuilder<KnowledgeDatabase>()
    } else {
      Room.databaseBuilder<KnowledgeDatabase>(
        (applicationSupportDirectory() / DATABASE_NAME).toString()
      )
    })
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(ioDispatcher)

internal actual fun defaultNpmDirectory(platformContext: Any): Path = applicationSupportDirectory()

internal actual suspend fun gunzip(source: Source): Source = GzipSource(source)

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

internal actual val systemFileSystem: FileSystem = FileSystem.SYSTEM

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): Path {
  val directory =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
      .first() as String
  NSFileManager.defaultManager.createDirectoryAtPath(
    directory,
    withIntermediateDirectories = true,
    attributes = null,
    error = null,
  )
  return directory.toPath()
}

private const val DATABASE_NAME = "knowledge.db"
