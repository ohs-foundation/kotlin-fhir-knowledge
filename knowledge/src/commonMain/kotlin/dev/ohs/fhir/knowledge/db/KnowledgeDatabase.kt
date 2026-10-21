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
package dev.ohs.fhir.knowledge.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import dev.ohs.fhir.knowledge.db.dao.KnowledgeDao
import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideEntity
import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideResourceMetadataEntity
import dev.ohs.fhir.knowledge.db.entities.ResourceMetadataEntity

/**
 * Stores the knowledge artifact metadata of implementation guides and the FHIR resources they
 * contain.
 *
 * The same FHIR resource (identified as the resource with the same `url`) can belong to several
 * implementation guides. To avoid duplicating it, [ImplementationGuideEntity] and
 * [ResourceMetadataEntity] are joined through [ImplementationGuideResourceMetadataEntity] in a
 * [many-to-many](https://developer.android.com/training/data-storage/room/relationships#many-to-many)
 * relationship.
 */
@Database(
  entities =
    [
      ImplementationGuideEntity::class,
      ResourceMetadataEntity::class,
      ImplementationGuideResourceMetadataEntity::class,
    ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(DbTypeConverters::class)
@ConstructedBy(KnowledgeDatabaseConstructor::class)
internal abstract class KnowledgeDatabase : RoomDatabase() {
  abstract fun knowledgeDao(): KnowledgeDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object KnowledgeDatabaseConstructor : RoomDatabaseConstructor<KnowledgeDatabase> {
  override fun initialize(): KnowledgeDatabase
}
