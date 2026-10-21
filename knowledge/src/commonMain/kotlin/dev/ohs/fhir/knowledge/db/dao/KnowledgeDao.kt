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
package dev.ohs.fhir.knowledge.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideEntity
import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideResourceMetadataEntity
import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideWithResources
import dev.ohs.fhir.knowledge.db.entities.ResourceMetadataEntity
import dev.ohs.fhir.model.r4.terminologies.ResourceType

@Dao
internal abstract class KnowledgeDao {

  @Transaction
  open suspend fun insertResource(
    implementationGuideId: Long?,
    resource: ResourceMetadataEntity,
  ): Long {
    val resourceMetadata =
      if (resource.url != null && resource.version != null) {
        getResourceWithUrlAndVersion(resource.resourceType, resource.url, resource.version)
      } else if (resource.url != null) {
        getResourceWithUrl(resource.resourceType, resource.url)
      } else {
        getResourcesWithNameAndVersion(resource.resourceType, resource.name, resource.version)
      }
    // TODO: compare the substantive part of the old and the new resource and raise if they differ.
    val resourceMetadataId = resourceMetadata?.resourceMetadataId ?: insert(resource)
    insert(ImplementationGuideResourceMetadataEntity(0, implementationGuideId, resourceMetadataId))
    return resourceMetadataId
  }

  /** Inserts the [implementationGuide] and its [resources] together, or none of them. */
  @Transaction
  open suspend fun insertImplementationGuide(
    implementationGuide: ImplementationGuideEntity,
    resources: List<ResourceMetadataEntity>,
  ): Long {
    val implementationGuideId = insert(implementationGuide)
    resources.forEach { insertResource(implementationGuideId, it) }
    return implementationGuideId
  }

  @Transaction
  open suspend fun deleteImplementationGuide(name: String, version: String) {
    val igEntity = getImplementationGuide(name, version)
    if (igEntity != null) {
      deleteImplementationGuide(igEntity)
      deleteOrphanedResources()
    }
  }

  @Query(
    "DELETE from ResourceMetadataEntity WHERE resourceMetadataId NOT IN (SELECT DISTINCT resourceMetadataId from ImplementationGuideResourceMetadataEntity)"
  )
  abstract suspend fun deleteOrphanedResources(): Int

  @Query("SELECT * from ImplementationGuideEntity")
  abstract suspend fun getImplementationGuides(): List<ImplementationGuideEntity>

  @Query(
    "SELECT * from ImplementationGuideEntity WHERE packageId = :packageId AND version = :version"
  )
  abstract suspend fun getImplementationGuide(
    packageId: String,
    version: String?,
  ): ImplementationGuideEntity?

  @Query("SELECT * from ResourceMetadataEntity")
  abstract suspend fun getResources(): List<ResourceMetadataEntity>

  @Query("SELECT * from ResourceMetadataEntity WHERE resourceType = :resourceType")
  abstract suspend fun getResources(resourceType: ResourceType): List<ResourceMetadataEntity>

  @Query(
    "SELECT * from ResourceMetadataEntity WHERE resourceType =:resourceType AND url = :url AND version = :version"
  )
  abstract suspend fun getResourceWithUrlAndVersion(
    resourceType: ResourceType,
    url: String,
    version: String,
  ): ResourceMetadataEntity?

  @Query("SELECT * from ResourceMetadataEntity WHERE resourceType = :resourceType AND url = :url")
  abstract suspend fun getResourceWithUrl(
    resourceType: ResourceType,
    url: String,
  ): ResourceMetadataEntity?

  @Deprecated("Load resources using URLs")
  @Query(
    "SELECT * from ResourceMetadataEntity WHERE  resourceType = :resourceType AND name = :name"
  )
  abstract suspend fun getResourcesWithName(
    resourceType: ResourceType,
    name: String?,
  ): List<ResourceMetadataEntity>

  @Query("SELECT * from ResourceMetadataEntity WHERE url = :url")
  abstract suspend fun getResource(url: String): List<ResourceMetadataEntity>

  @Query("SELECT * from ResourceMetadataEntity WHERE url = :url AND version = :version")
  abstract suspend fun getResource(url: String, version: String): ResourceMetadataEntity?

  @Query("SELECT * from ResourceMetadataEntity WHERE resourceMetadataId = :id")
  abstract suspend fun getResourcesWithId(id: Long?): ResourceMetadataEntity?

  @Query(
    "SELECT * from ResourceMetadataEntity WHERE resourceType = :resourceType AND name = :name AND version = :version"
  )
  abstract suspend fun getResourcesWithNameAndVersion(
    resourceType: ResourceType,
    name: String?,
    version: String?,
  ): ResourceMetadataEntity?

  @Transaction
  @Query("SELECT * FROM ImplementationGuideEntity WHERE implementationGuideId = :igId")
  abstract suspend fun getImplementationGuidesWithResources(
    igId: Long
  ): ImplementationGuideWithResources?

  @Delete abstract suspend fun deleteImplementationGuide(igEntity: ImplementationGuideEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(resource: ResourceMetadataEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(igResourceXRef: ImplementationGuideResourceMetadataEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(ig: ImplementationGuideEntity): Long
}
