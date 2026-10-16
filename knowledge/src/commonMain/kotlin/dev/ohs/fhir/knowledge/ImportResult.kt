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

import okio.Path

/**
 * What importing a FHIR NPM package did with each entry of its directory.
 *
 * @property indexed the number of knowledge artifacts indexed.
 * @property skipped the number of entries holding no knowledge artifact, such as `package.json`,
 *   resources of other types, and subdirectories.
 * @property failed the files that name a knowledge artifact type but could not be read or
 *   deserialized. A non-empty list means the package was only partly imported.
 */
data class ImportResult(val indexed: Int, val skipped: Int, val failed: List<Path>)
