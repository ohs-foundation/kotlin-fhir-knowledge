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
import okio.Path

/**
 * A database entity holding the metadata of an
 * [Implementation Guide](https://build.fhir.org/implementationguide.html).
 *
 * It stores as much metadata as an NPM package manager needs to resolve the
 * [dependency](https://build.fhir.org/implementationguide-definitions.html#ImplementationGuide.dependsOn)
 * on another implementation guide.
 */
@Entity(
  indices =
    [
      Index(value = ["implementationGuideId"]),
      Index(value = ["packageId", "url", "version"], unique = true),
    ]
)
internal data class ImplementationGuideEntity(
  @PrimaryKey(autoGenerate = true) val implementationGuideId: Long,
  val url: String,
  val packageId: String,
  val version: String?,
  /** Directory the implementation guide files are stored in. */
  val rootDirectory: Path,
)
