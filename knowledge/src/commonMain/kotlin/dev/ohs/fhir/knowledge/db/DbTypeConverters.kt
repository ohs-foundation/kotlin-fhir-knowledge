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

import androidx.room3.TypeConverter
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import okio.Path
import okio.Path.Companion.toPath

/**
 * Maps the two entity column types SQLite has no representation for.
 *
 * A [Path] is stored as the string it prints as, and a [ResourceType] as its FHIR code. Room 3
 * would otherwise persist the enum by its Kotlin constant name, which is the same text today but
 * would drift the moment a code and its constant diverge.
 */
internal object DbTypeConverters {

  @TypeConverter fun filePathToFile(filePath: String): Path = filePath.toPath()

  @TypeConverter fun fileToFilePath(file: Path): String = file.toString()

  @TypeConverter fun codeToResourceType(code: String): ResourceType = ResourceType.fromCode(code)

  @TypeConverter fun resourceTypeToCode(resourceType: ResourceType): String = resourceType.code
}
