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

import dev.ohs.fhir.knowledge.db.entities.ImplementationGuideEntity
import dev.ohs.fhir.knowledge.db.entities.ResourceMetadataEntity
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

internal class KnowledgeDatabaseTest {
  private val knowledgeDb = inMemoryKnowledgeDatabase()
  private val knowledgeDao = knowledgeDb.knowledgeDao()

  @AfterTest
  fun closeDb() {
    knowledgeDb.close()
  }

  @Test
  fun shouldInsertAnImplementationGuide() = runTest {
    assertTrue(knowledgeDao.insert(IG_ENTITY) > 0L)

    assertEquals(listOf(IG_PACKAGE_ID), knowledgeDao.getImplementationGuides().map { it.packageId })
  }

  @Test
  fun shouldLinkAnInsertedResourceToItsImplementationGuide() = runTest {
    val igId = knowledgeDao.insert(IG_ENTITY)

    knowledgeDao.insertResource(igId, resourceEntity())

    assertEquals(listOf(RES_URL), knowledgeDao.getResources(ResourceType.ValueSet).map { it.url })
    assertEquals(emptyList(), knowledgeDao.getResources(ResourceType.Account))
    assertEquals(listOf(RES_URL), resourceUrlsOf(igId))
    assertNull(knowledgeDao.getImplementationGuidesWithResources(UNKNOWN_IG_ID))
  }

  @Test
  fun shouldDeleteTheResourcesLeftOrphanedByADeletedGuide() = runTest {
    val igId = knowledgeDao.insert(IG_ENTITY)
    knowledgeDao.insertResource(igId, resourceEntity())

    knowledgeDao.deleteImplementationGuide(IG_PACKAGE_ID, IG_VERSION)

    assertEquals(emptyList(), knowledgeDao.getImplementationGuides())
    assertEquals(emptyList(), knowledgeDao.getResources())
    assertNull(knowledgeDao.getImplementationGuidesWithResources(igId))
  }

  @Test
  fun shouldInsertAnImplementationGuideTogetherWithItsResources() = runTest {
    val igId = knowledgeDao.insertImplementationGuide(IG_ENTITY, listOf(resourceEntity()))

    assertEquals(listOf(RES_URL), resourceUrlsOf(igId))
  }

  /**
   * The second resource shares the url, version and file of the first under another type, which the
   * unique index of the resource table rejects.
   */
  @Test
  fun shouldInsertNothingWhenOneResourceOfAnImplementationGuideCannotBeInserted() = runTest {
    val resources =
      listOf(resourceEntity(), resourceEntity().copy(resourceType = ResourceType.Library))

    assertFails { knowledgeDao.insertImplementationGuide(IG_ENTITY, resources) }

    assertEquals(emptyList(), knowledgeDao.getImplementationGuides())
    assertEquals(emptyList(), knowledgeDao.getResources())
  }

  /** One resource can belong to several guides, so the row is shared rather than duplicated. */
  @Test
  fun shouldShareOneResourceRowBetweenTwoImplementationGuides() = runTest {
    val igId1 = knowledgeDao.insert(IG_ENTITY)
    val igId2 = knowledgeDao.insert(IG_ENTITY.copy(version = "2.0.0"))

    knowledgeDao.insertResource(igId1, resourceEntity())
    knowledgeDao.insertResource(igId2, resourceEntity())

    assertEquals(listOf(RES_URL), resourceUrlsOf(igId1))
    assertEquals(listOf(RES_URL), resourceUrlsOf(igId2))
    assertEquals(1, knowledgeDao.getResources().size)
  }

  /** A shared resource outlives the first of its guides to be deleted. */
  @Test
  fun shouldKeepASharedResourceWhileAnotherGuideStillHoldsIt() = runTest {
    val igId1 = knowledgeDao.insert(IG_ENTITY)
    val igId2 = knowledgeDao.insert(IG_ENTITY.copy(version = "2.0.0"))
    knowledgeDao.insertResource(igId1, resourceEntity())
    knowledgeDao.insertResource(igId2, resourceEntity())

    knowledgeDao.deleteImplementationGuide(IG_PACKAGE_ID, IG_VERSION)

    assertEquals(listOf(RES_URL), resourceUrlsOf(igId2))
    assertEquals(1, knowledgeDao.getResources().size)
  }

  /**
   * android-fhir declares this query non-null, so Room raises when the row is absent. Returning
   * null is what its `listOfNotNull` call site expects, and what `getResource(url)` already does.
   */
  @Test
  fun shouldReturnNothingForAnUnknownResource() = runTest {
    assertNull(knowledgeDao.getResource(RES_URL, RES_VERSION))
    assertEquals(emptyList(), knowledgeDao.getResource(RES_URL))
  }

  private suspend fun resourceUrlsOf(igId: Long) =
    knowledgeDao.getImplementationGuidesWithResources(igId)?.resources?.map { it.url }

  private fun resourceEntity() =
    ResourceMetadataEntity(0L, ResourceType.ValueSet, RES_URL, RES_NAME, RES_VERSION, RES_FILE)

  private companion object {
    const val IG_PACKAGE_ID = "test.ig"
    const val IG_VERSION = "1.0.0"
    const val RES_NAME = "res-name-1"
    const val RES_VERSION = "1.0.0"
    const val RES_URL = "http://url.com/ValueSet/$RES_NAME/$RES_VERSION"
    const val UNKNOWN_IG_ID = -1L
    val RES_FILE = "resId".toPath()
    val IG_ENTITY =
      ImplementationGuideEntity(
        implementationGuideId = 0L,
        packageId = IG_PACKAGE_ID,
        version = IG_VERSION,
        url = "http://url",
        rootDirectory = "test".toPath(),
      )
  }
}
