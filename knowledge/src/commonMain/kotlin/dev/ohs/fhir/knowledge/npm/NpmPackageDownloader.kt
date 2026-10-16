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
package dev.ohs.fhir.knowledge.npm

import dev.ohs.fhir.knowledge.FhirNpmPackage
import kotlin.coroutines.cancellation.CancellationException
import okio.IOException
import okio.Path

/** Downloads an NPM package from the configured package server. */
fun interface NpmPackageDownloader {
  /**
   * Downloads the [fhirNpmPackage] and unpacks it into [packageFolder].
   *
   * @throws IOException if the package cannot be fetched or unpacked. Declared so that the failure
   *   surfaces as an `NSError` to Swift callers rather than aborting.
   */
  @Throws(IOException::class, CancellationException::class)
  suspend fun downloadPackage(fhirNpmPackage: FhirNpmPackage, packageFolder: Path)
}
