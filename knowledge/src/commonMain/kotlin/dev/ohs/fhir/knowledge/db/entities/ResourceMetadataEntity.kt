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
package dev.ohs.fhir.knowledge.db.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import okio.Path

/**
 * A database entity holding the description and metadata of a
 * [FHIR resource](https://build.fhir.org/canonicalresource.html).
 */
@Entity(
  indices =
    [
      Index(value = ["resourceMetadataId"]),
      Index(value = ["url", "version", "resourceFile"], unique = true),
    ]
)
internal data class ResourceMetadataEntity(
  @PrimaryKey(autoGenerate = true) val resourceMetadataId: Long,
  val resourceType: ResourceType,
  val url: String?,
  val name: String?,
  val version: String?,
  /** Location of the JSON file holding the full resource. */
  val resourceFile: Path,
)
