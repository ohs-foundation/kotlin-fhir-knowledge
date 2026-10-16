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
 * What a downloaded FHIR NPM package's manifest says about it, read back out of the local cache.
 *
 * @property dependencies the packages this one depends on, which the knowledge manager installs
 *   transitively.
 * @property rootDirectory the unpacked `package` directory holding the FHIR resources.
 */
data class LocalFhirNpmPackageMetadata(
  val packageId: String,
  val version: String,
  val canonical: String?,
  val dependencies: List<FhirNpmPackage>,
  val rootDirectory: Path,
)
